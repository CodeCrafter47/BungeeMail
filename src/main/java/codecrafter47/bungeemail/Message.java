package codecrafter47.bungeemail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Created by florian on 15.11.14.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Message {
    String senderName;
    UUID senderUUID;
    UUID recipient;
    String message;
    boolean read;
    long time;

    @Override
    public int hashCode() {
        return (int) (senderUUID.hashCode() + recipient.hashCode() + time);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Message) {
            Message that = (Message) obj;
            return that.senderUUID.equals(senderUUID) && that.recipient.equals(recipient) && that.time == time;
        }
        return super.equals(obj);
    }
}
