package codecrafter47.bungeemail;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IStorageBackend {
    List<Message> getMessagesFor(UUID uuid, boolean onlyNew) throws StorageException;

    void saveMessage(Message message) throws StorageException;

    void markRead(Message message) throws StorageException;

    void delete(Message message) throws StorageException;

    UUID getUUIDForName(String name) throws StorageException;

    Collection<UUID> getAllKnownUUIDs() throws StorageException;

    // used for tab-complete
    Collection<String> getKnownUsernames() throws StorageException;

    void updateUserEntry(UUID uuid, String username) throws StorageException;

    void delete(int id) throws StorageException;

    void deleteOlder(long time, boolean deleteUnread) throws StorageException;
}
