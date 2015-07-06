package codecrafter47.bungeemail;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Created by florian on 15.11.14.
 */
public interface IStorageBackend {
    public List<Message> getMessagesFor(UUID uuid, boolean onlyNew);

    public void saveMessage(Message message);

    public void markRead(Message message);

    public void delete(Message message);

    public UUID getUUIDForName(String name);

    public Collection<UUID> getAllKnownUUIDs();

    // used for tab-complete
    public Collection<String> getKnownUsernames();

    void delete(int id);

    void deleteOlder(long time, boolean deleteUnread);
}
