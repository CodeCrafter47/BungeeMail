package codecrafter47.bungeemail;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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
    private ReadWriteLock mailLock = new ReentrantReadWriteLock();
    private ReadWriteLock uuidLock = new ReentrantReadWriteLock();
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
                    FileInputStream fin = new FileInputStream(saveFile);
                    Reader reader = new InputStreamReader(fin, Charsets.UTF_8);
                    data = gson.fromJson(reader, Data.class);
                    reader.close();
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
            mailLock.readLock().lock();
            uuidLock.readLock().lock();
            fileLock.writeLock().lock();
            try {
                if (tmpSaveFile.exists()) {
                    if (!tmpSaveFile.delete()) return false;
                }
                if (!tmpSaveFile.createNewFile()) return false;
                Writer writer = new OutputStreamWriter(new FileOutputStream(tmpSaveFile), Charsets.UTF_8);
                gson.toJson(data, writer);
                writer.close();
                if (saveFile.exists()) {
                    if (!saveFile.delete()) return false;
                }
                return tmpSaveFile.renameTo(saveFile);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to save file to disk", ex);
                return false;
            } finally {
                fileLock.writeLock().unlock();
                uuidLock.readLock().unlock();
                mailLock.readLock().unlock();
            }
        }
        return true;
    }

    /**
     * called by all methods of this class that modify the data set to request a save.
     */
    private void requestSave() {
        saveRequested = true;
    }

    @Override
    public List<Message> getMessagesFor(UUID uuid, boolean onlyNew) throws StorageException {
        mailLock.readLock().lock();
        try {
            ArrayList<Message> messages = new ArrayList<>();
            for (Message message : data.data) {
                if (message.getRecipient().equals(uuid) && (!message.isRead() || !onlyNew)) messages.add(message);
            }
            return messages;
        } finally {
            mailLock.readLock().unlock();
        }
    }

    @Override
    public Message saveMessage(String senderName, UUID senderUUID, UUID recipient, String message, boolean read, long time) throws StorageException {
        mailLock.writeLock().lock();
        try {
            FlatFileMessage mail = new FlatFileMessage(time, read, message, recipient, senderUUID, senderName);
            data.data.add(mail);
            requestSave();
            return mail;
        } finally {
            mailLock.writeLock().unlock();
        }
    }

    @Override
    public void markRead(Message message) throws StorageException {
        Preconditions.checkArgument(message instanceof FlatFileMessage);
        mailLock.writeLock().lock();
        try {
            ((FlatFileMessage) message).setRead(true);
            requestSave();
        } finally {
            mailLock.writeLock().unlock();
        }
    }

    @Override
    public void delete(Message message) throws StorageException {
        Preconditions.checkArgument(message instanceof FlatFileMessage);
        mailLock.writeLock().lock();
        try {
            data.data.remove(message);
            requestSave();
        } finally {
            mailLock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(long id, UUID recipient) throws StorageException {
        boolean deleted = false;
        mailLock.writeLock().lock();
        try {
            Iterator<FlatFileMessage> iterator = data.data.iterator();
            while (iterator.hasNext()) {
                Message message = iterator.next();
                if (message.getId() == id && message.getRecipient().equals(recipient)) {
                    deleted = true;
                    iterator.remove();
                }
            }
            requestSave();
        } finally {
            mailLock.writeLock().unlock();
        }
        return deleted;
    }

    @Override
    public void deleteOlder(long time, boolean deleteUnread) throws StorageException {
        mailLock.writeLock().lock();
        try {
            for (Iterator<FlatFileMessage> iterator = data.data.iterator(); iterator.hasNext(); ) {
                Message message = iterator.next();
                if (message.getTime() < time && (deleteUnread || message.isRead())) {
                    iterator.remove();
                }
            }
            requestSave();
        } finally {
            mailLock.writeLock().unlock();
        }
    }

    @Override
    public UUID getUUIDForName(String name) throws StorageException {
        if ("Console".equals(name)) {
            return BungeeMail.CONSOLE_UUID;
        }
        uuidLock.readLock().lock();
        try {
            UUID uuid = data.uuidMap.get(name);
            if (uuid == null) {
                // TODO better performance?
                for (Map.Entry<String, UUID> entry : data.uuidMap.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(name)) {
                        uuid = entry.getValue();
                    }
                }
            }
            return uuid;
        } finally {
            uuidLock.readLock().unlock();
        }
    }

    @Override
    public Collection<UUID> getAllKnownUUIDs() throws StorageException {
        uuidLock.readLock().lock();
        try {
            return data.uuidMap.values();
        } finally {
            uuidLock.readLock().unlock();
        }
    }

    @Override
    public Collection<String> getKnownUsernames() throws StorageException {
        uuidLock.readLock().lock();
        try {
            return data.uuidMap.keySet();
        } finally {
            uuidLock.readLock().unlock();
        }
    }

    @Override
    public void updateUserEntry(UUID uuid, String username) throws StorageException {
        uuidLock.writeLock().lock();
        try {
            data.uuidMap.put(username, uuid);
            requestSave();
        } finally {
            uuidLock.writeLock().unlock();
        }
    }

    private static class FlatFileMessage implements Message {
        private String senderName;
        private UUID senderUUID;
        private UUID recipient;
        private String message;
        private boolean read;
        private long time;
        private transient final long id;

        private static AtomicLong idSupplier = new AtomicLong(1);

        public FlatFileMessage(long time, boolean read, String message, UUID recipient, UUID senderUUID, String senderName) {
            this();
            this.time = time;
            this.read = read;
            this.message = message;
            this.recipient = recipient;
            this.senderUUID = senderUUID;
            this.senderName = senderName;
        }

        public FlatFileMessage() {
            id = idSupplier.getAndIncrement();
        }

        @Override
        public String getSenderName() {
            return senderName;
        }

        @Override
        public UUID getSenderUUID() {
            return senderUUID;
        }

        @Override
        public UUID getRecipient() {
            return recipient;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public boolean isRead() {
            return read;
        }

        @Override
        public long getTime() {
            return time;
        }

        @Override
        public long getId() {
            return id;
        }

        private void setRead(boolean read) {
            this.read = read;
        }

        @Override
        public int hashCode() {
            return Long.valueOf(id).hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FlatFileMessage && ((FlatFileMessage) other).getId() == getId();
        }
    }

    private static class Data {
        private List<FlatFileMessage> data = new ArrayList<>();
        private Map<String, UUID> uuidMap = new HashMap<>();
    }
}
