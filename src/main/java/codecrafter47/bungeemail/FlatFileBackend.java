package codecrafter47.bungeemail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FlatFileBackend implements IStorageBackend {
    private Logger logger;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File saveFile;
    private File tmpSaveFile;
    private Data data;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private boolean saveRequested = false;

    public FlatFileBackend(BungeeMail plugin) {
        logger = plugin.getLogger();
        tmpSaveFile = new File(plugin.getDataFolder(), "data.json.tmp");
        saveFile = new File(plugin.getDataFolder(), "data.json");
    }

    /**
     * Attempts to read the mail data from a file
     *
     * @return true on success
     */
    public boolean readData() {
        fileLock.readLock().lock();
        try {
            if (saveFile.exists()) {
                try {
                    FileReader fileReader = new FileReader(saveFile);
                    data = gson.fromJson(fileReader, Data.class);
                    fileReader.close();
                    return true;
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Failed to read data.gson", ex);
                    data = new Data();
                }
            } else if (tmpSaveFile.exists()) {
                if (tmpSaveFile.renameTo(saveFile)) {
                    return readData();
                }
            } else {
                data = new Data();
                return true;
            }
            return false;
        } finally {
            fileLock.readLock().unlock();
        }
    }

    /**
     * Attempts to save the mail data to a file
     *
     * @return true on success, false otherwise
     */
    public boolean saveData() {
        if (saveRequested) {
            saveRequested = false;
            lock.readLock().lock();
            fileLock.writeLock().lock();
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
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to save file to disk", ex);
                return false;
            } finally {
                fileLock.writeLock().unlock();
                lock.readLock().unlock();
            }
        }
        return true;
    }

    /**
     * called by all methods of this class that modify the data set to request a save.
     */
    private void requestSave(){
        saveRequested = true;
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
            requestSave();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void markRead(Message message) {
        lock.writeLock().lock();
        try {
            message.setRead(true);
            requestSave();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(Message message) {
        lock.writeLock().lock();
        try {
            data.data.remove(message);
            requestSave();
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
            requestSave();
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
            requestSave();
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

    @Override
    public void updateUserEntry(UUID uuid, String username) {
        lock.writeLock().lock();
        try {
            data.uuidMap.put(username, uuid);
            requestSave();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static class Data {
        private List<Message> data = new ArrayList<>();
        private Map<String, UUID> uuidMap = new HashMap<>();
    }
}
