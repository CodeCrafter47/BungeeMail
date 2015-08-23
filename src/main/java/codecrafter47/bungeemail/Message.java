package codecrafter47.bungeemail;

import java.util.UUID;

public interface Message {

    String getSenderName();

    UUID getSenderUUID();

    UUID getRecipient();

    String getMessage();

    boolean isRead();

    long getTime();

    long getId();
}
