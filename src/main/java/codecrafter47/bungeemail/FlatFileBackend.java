package codecrafter47.bungeemail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.SneakyThrows;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FlatFileBackend implements IStorageBackend, Listener {
    private Logger logger;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File saveFile;
    private File tmpSaveFile;
    private Data data;
    private ReadWriteLock lock;

    public FlatFileBackend(BungeeMail plugin) {
        logger = plugin.getLogger();
        saveFile = new File(plugin.getDataFolder(), "data.json");
        tmpSaveFile = new File(plugin.getDataFolder(), "data.json.tmp");
        lock = new ReentrantReadWriteLock();
        readData();
        plugin.getProxy().getPluginManager().registerListener(plugin, this);
    }

    @SneakyThrows
    private void readData() {
        // This method is only called during construction of the class,
        // so no synchronization is needed.
        if (saveFile.exists()) {
            try {
                FileReader fileReader = new FileReader(saveFile);
                data = gson.fromJson(fileReader, Data.class);
                fileReader.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                data = new Data();
            }
        } else {
            data = new Data();
        }
    }

    /**
     * Attempts to save the mail data to a file
     * @return true on success, false otherwise
     */
    private boolean saveData() {
        try {
            if (tmpSaveFile.exists()) {
                if (!tmpSaveFile.delete()) return false;
            }
            if (!tmpSaveFile.createNewFile()) return false;
            FileWriter fileWriter = new FileWriter(tmpSaveFile);
            gson.toJson(data, fileWriter);
            fileWriter.close();
            if (saveFile.exists()) {
                if (!saveFile.delete()) return false;
            }
            return tmpSaveFile.renameTo(saveFile);
        } catch (IOException ex){
            logger.log(Level.WARNING, "Failed to save file to disk", ex);
            return false;
        }
    }

    @Override
    public List<Message> getMessagesFor(UUID uuid, boolean onlyNew) {
        lock.readLock().lock();
        try {
            ArrayList<Message> messages = new ArrayList<>();
            for (Message message : data.data) {
                if (message.getRecipient().equals(uuid) && (!message.isRead() || !onlyNew)) messages.add(message);
            }
            return messages;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void saveMessage(Message message) {
        lock.writeLock().lock();
        try {
            if (!data.data.contains(message)) {
                data.data.add(message);
            }
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void markRead(Message message) {
        lock.writeLock().lock();
        try {
            message.setRead(true);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(Message message) {
        lock.writeLock().lock();
        try {
            data.data.remove(message);
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(int id) {
        lock.writeLock().lock();
        try {
            Iterator<Message> iterator = data.data.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().hashCode() == id) {
                    iterator.remove();
                }
            }
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteOlder(long time, boolean deleteUnread) {
        lock.writeLock().lock();
        try {
            for (Iterator<Message> iterator = data.data.iterator(); iterator.hasNext(); ) {
                Message message = iterator.next();
                if (message.getTime() < time && (deleteUnread || message.isRead())) {
                    iterator.remove();
                }
            }
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public UUID getUUIDForName(String name) {
        lock.readLock().lock();
        try {
            return data.uuidMap.get(name);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<UUID> getAllKnownUUIDs() {
        lock.readLock().lock();
        try {
            return data.uuidMap.values();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<String> getKnownUsernames() {
        lock.readLock().lock();
        try {
            return data.uuidMap.keySet();
        } finally {
            lock.readLock().unlock();
        }
    }

    @EventHandler
    public void onJoin(PostLoginEvent event) {
        lock.writeLock().lock();
        try {
            data.uuidMap.put(event.getPlayer().getName(), event.getPlayer().getUniqueId());
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static class Data {
        private List<Message> data = new ArrayList<>();
        private Map<String, UUID> uuidMap = new HashMap<>();
    }
}
