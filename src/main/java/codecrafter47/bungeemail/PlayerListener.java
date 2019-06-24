package codecrafter47.bungeemail;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private BungeeMail plugin;
    private final TabCompleteCache tabCompleteCache;

    public PlayerListener(BungeeMail plugin, TabCompleteCache tabCompleteCache) {
        this.plugin = plugin;
        this.tabCompleteCache = tabCompleteCache;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(LoginEvent event) {
        if (!event.isCancelled()) {
            final UUID uniqueId = event.getConnection().getUniqueId();
            final String name = event.getConnection().getName();
            ProxyServer.getInstance().getScheduler().runAsync(plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        plugin.getStorage().updateUserEntry(uniqueId, name);
                    } catch (StorageException e) {
                        plugin.getLogger().log(Level.SEVERE, "Unable to update a players uuid in the cache", e);
                    }
                }
            });
        }
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        final ProxiedPlayer player = event.getPlayer();
        if (!plugin.config.getBoolean("showMailsOnServerSwitch")) {
            showNewMailInfo(player);
        }
    }

    @EventHandler
    public void onPlayerServerSwitch(ServerSwitchEvent event) {
        final ProxiedPlayer player = event.getPlayer();
        if (plugin.config.getBoolean("showMailsOnServerSwitch")) {
            showNewMailInfo(player);
        }
    }

    private void showNewMailInfo(final ProxiedPlayer player) {
        plugin.getProxy().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.config.getBoolean("showMailsOnLogin")) {
                    try {
                        plugin.listMessages(player, 1, false, false);
                    } catch (StorageException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to show mails to player", e);
                    }
                } else {
                    plugin.showLoginInfo(player);
                }
            }
        }, 1, TimeUnit.SECONDS);
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        String commandLine = event.getCursor();
        if (commandLine.startsWith("/" + plugin.config.getString("mail_command"))) {

            event.getSuggestions().clear();

            Connection sender = event.getSender();
            if (!(sender instanceof ProxiedPlayer)) {
                return;
            }
            ProxiedPlayer player = (ProxiedPlayer) sender;

            if (player.hasPermission(Permissions.COMMAND)) {

                String[] args = commandLine.split(" ", -1);
                String prefix = args[args.length - 1];

                if (args.length == 2) {
                    if ("list".startsWith(prefix)) {
                        event.getSuggestions().add("list");
                    }
                    if ("listall".startsWith(prefix)) {
                        event.getSuggestions().add("listall");
                    }
                    if (player.hasPermission(Permissions.COMMAND_SEND) && "send".startsWith(prefix)) {
                        event.getSuggestions().add("send");
                    }
                    if (player.hasPermission(Permissions.COMMAND_SENDALL) && "sendall".startsWith(prefix)) {
                        event.getSuggestions().add("sendall");
                    }
                    if ("help".startsWith(prefix)) {
                        event.getSuggestions().add("help");
                    }
                    if ("del".startsWith(prefix)) {
                        event.getSuggestions().add("del");
                    }
                    if (player.hasPermission(Permissions.COMMAND_ADMIN) && "reload".startsWith(prefix)) {
                        event.getSuggestions().add("reload");
                    }
                }
                if (args.length == 3 && "del".equals(args[1])) {
                    if ("read".equals(prefix)) {
                        event.getSuggestions().add("read");
                    }
                    if ("all".equals(prefix)) {
                        event.getSuggestions().add("all");
                    }
                }
                if ((args.length == 3 && "send".equals(args[1]))
                        || (args.length == 2 && event.getSuggestions().isEmpty())) {
                    if (player.hasPermission(Permissions.COMMAND_SEND) && tabCompleteCache != null) {
                        event.getSuggestions().addAll(tabCompleteCache.getSuggestions(prefix));
                        Collections.sort(event.getSuggestions(), CaseInsensitiveComparator.INSTANCE);
                    }
                }
            }
        }
    }
}
