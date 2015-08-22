package codecrafter47.bungeemail;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.concurrent.TimeUnit;

/**
 * Created by florian on 15.11.14.
 */
public class PlayerListener implements Listener {

    private BungeeMail plugin;

    public PlayerListener(BungeeMail plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(LoginEvent event) {
        if (!event.isCancelled()) {
            plugin.getStorage().updateUserEntry(event.getConnection().getUniqueId(), event.getConnection().getName());
        }
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        final ProxiedPlayer player = event.getPlayer();
        plugin.getProxy().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.config.getBoolean("showMailsOnLogin", true)) {
                    plugin.listMessages(player, 1, false, false);
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
            for (String player : plugin.getStorage().getKnownUsernames()) {
                if (player.contains(begin)) {
                    event.getSuggestions().add(player);
                }
            }
        }
    }
}
