package codecrafter47.bungeemail;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private BungeeMail plugin;

    public PlayerListener(BungeeMail plugin) {
        this.plugin = plugin;
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
        plugin.getProxy().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.config.getBoolean("showMailsOnLogin", true)) {
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
        if (commandLine.startsWith("/mail")) {
            event.getSuggestions().clear();
            String[] split = commandLine.split(" ");
            String begin = split[split.length - 1];
            try {
                for (String player : plugin.getStorage().getKnownUsernames()) {
                    if (player.contains(begin)) {
                        event.getSuggestions().add(player);
                    }
                }
            } catch (StorageException e) {
                plugin.getLogger().log(Level.WARNING, "An error occurred while accessing usernames for tab completion", e);
            }
        }
    }
}
