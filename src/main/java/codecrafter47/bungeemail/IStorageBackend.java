package codecrafter47.bungeemail;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Created by florian on 15.11.14.
 */
public interface IStorageBackend {
    List<Message> getMessagesFor(UUID uuid, boolean onlyNew);

    void saveMessage(Message message);

    void markRead(Message message);

    void delete(Message message);

    UUID getUUIDForName(String name);

    Collection<UUID> getAllKnownUUIDs();

    // used for tab-complete
    Collection<String> getKnownUsernames();

    void updateUserEntry(UUID uuid, String username);

    void delete(int id);

    void deleteOlder(long time, boolean deleteUnread);
}
