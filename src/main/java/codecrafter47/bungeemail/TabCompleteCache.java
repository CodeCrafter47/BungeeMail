package codecrafter47.bungeemail;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TabCompleteCache {

    private final Plugin plugin;
    private final IStorageBackend backend;

    private ArrayList<String> sortedNames = new ArrayList<>();

    public TabCompleteCache(Plugin plugin, final IStorageBackend backend) {
        this.plugin = plugin;
        this.backend = backend;
        updateCache(0);
    }

    private void updateCache(final int wait) {
        plugin.getProxy().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
                ArrayList<String> usernames = null;
                try {
                    usernames = new ArrayList<>(backend.getKnownUsernames());
                } catch (StorageException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to get tab completion data", e);
                    return;
                }
                Collections.sort(usernames, CaseInsensitiveComparator.INSTANCE);
                sortedNames = usernames;
            }
        }, wait, TimeUnit.MINUTES);
    }

    private void scheduleNextCacheUpdate() {
        if (sortedNames.size() < 5000) {
            updateCache(5);
        } else {
            updateCache(60);
        }
    }

    public Set<String> getSuggestions(String prefix) {
        Set<String> suggestions = new HashSet<>();
        // add suggestions from cache
        String prefixLower = prefix.toLowerCase();
        int i = Collections.binarySearch(sortedNames, prefixLower, CaseInsensitiveComparator.INSTANCE);
        if (i < 0) {
            i = -i-1;
        }
        int n = 0;
        while (i < sortedNames.size() && sortedNames.get(i).toLowerCase().startsWith(prefixLower) && n < 100) {
            suggestions.add(sortedNames.get(i));
            i++;
            n++;
        }
        // search online players
        for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
            if (player.getName().toLowerCase().startsWith(prefixLower)) {
                suggestions.add(player.getName());
            }
        }
        return suggestions;
    }

}
