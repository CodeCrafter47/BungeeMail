package codecrafter47.bungeemail;

import com.google.common.base.Preconditions;
import lib.PatPeter.SQLibrary.Database;
import lib.PatPeter.SQLibrary.MySQL;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MySQLBackend implements IStorageBackend {

    BungeeMail plugin;
    Database sql;

    public MySQLBackend(BungeeMail plugin) {
        this.plugin = plugin;
        sql = new MySQL(plugin.getLogger(), "[BungeeMail/MySQL]", plugin.config.getString("mysql_hostname"),
                plugin.config.getInt("mysql_port"), plugin.config.getString("mysql_database"),
                plugin.config.getString("mysql_username"), plugin.config.getString("mysql_password"));
        try {
            sql.open();
            if (sql.isOpen()) {
                sql.query("CREATE TABLE IF NOT EXISTS bungeemail_mails (id int NOT NULL AUTO_INCREMENT,senderName varchar(20), senderUUID varchar(40), recipient varchar(40), `message` varchar(255), `read` boolean, `time` bigint, PRIMARY KEY (id))");
                sql.query("CREATE TABLE IF NOT EXISTS bungeemail_uuids (id int NOT NULL AUTO_INCREMENT,username varchar(20), uuid varchar(40),PRIMARY KEY (id))");
            } else {
                throw new RuntimeException("Failed to connect to MySql database");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setup failed");
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Message> getMessagesFor(UUID uuid, boolean onlyNew) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps;
            ResultSet rs;
            if (onlyNew) {
                ps = sql.prepare("select * from bungeemail_mails where recipient=? and `read`='0'");
            } else {
                ps = sql.prepare("select * from bungeemail_mails where recipient=?");
            }
            ps.setString(1, uuid.toString());
            rs = sql.query(ps);
            ArrayList<Message> messages = new ArrayList<>();
            while (rs.next()) {
                String senderName = rs.getString("senderName");
                UUID senderUUID = UUID.fromString(rs.getString("senderUUID"));
                UUID recipient = UUID.fromString(rs.getString("recipient"));
                String message = rs.getString("message");
                boolean read = rs.getBoolean("read");
                long time = rs.getLong("time");
                long id = rs.getLong("id");
                messages.add(new SQLMessage(id, senderName, senderUUID, recipient, message, read, time));
            }
            return messages;
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public Message saveMessage(String senderName, UUID senderUUID, UUID recipient, String message, boolean read, long time) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps = sql.prepare("insert into bungeemail_mails values(NULL, ?, ?, ?, ?, ?, ?)");
            ps.setString(1, senderName);
            ps.setString(2, senderUUID.toString());
            ps.setString(3, recipient.toString());
            ps.setString(4, message);
            ps.setBoolean(5, read);
            ps.setLong(6, time);
            ArrayList<Long> ids = sql.insert(ps);
            if(ids.size() != 1){
                throw new StorageException("Saving mail failed. " + ids.size() + " rows inserted. But should be 1");
            }
            return new SQLMessage(ids.get(0), senderName, senderUUID, recipient, message, read, time);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void markRead(Message message) throws StorageException {
        Preconditions.checkArgument(message instanceof SQLMessage);
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps = sql.prepare("update bungeemail_mails set `read`=1 where id=?");
            ps.setLong(1, message.hashCode());
            sql.query(ps);
            ((SQLMessage)message).setRead(true);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void delete(Message message) throws StorageException {
        delete(message.getId());
    }

    @Override
    public void delete(long id) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps = sql.prepare("delete from bungeemail_mails where id=?");
            ps.setLong(1, id);
            sql.query(ps);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteOlder(long time, boolean deleteUnread) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps;
            if (deleteUnread) {
                ps = sql.prepare("delete from bungeemail_mails where time < ?");
            } else {
                ps = sql.prepare("delete from bungeemail_mails where time < ? and `read`=0");
            }
            ps.setLong(1, time);
            sql.query(ps);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public UUID getUUIDForName(String name) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps = sql.prepare("select * from bungeemail_uuids where username=?");
            ps.setString(1, name);
            ResultSet rs = sql.query(ps);
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
            return null;
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public Collection<UUID> getAllKnownUUIDs() throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            HashSet<UUID> uuids = new HashSet<>();
            ResultSet rs = sql.query("select * from bungeemail_uuids");
            while (rs.next()) {
                uuids.add(UUID.fromString(rs.getString("uuid")));
            }
            return uuids;
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public Collection<String> getKnownUsernames() throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            HashSet<String> strings = new HashSet<>();
            ResultSet rs = sql.query("select * from bungeemail_uuids");
            while (rs.next()) {
                strings.add(rs.getString("username"));
            }
            return strings;
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void updateUserEntry(final UUID uuid, final String username) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps = sql.prepare("select * from bungeemail_uuids where uuid=?");
            ps.setString(1, uuid.toString());
            ResultSet rs = sql.query(ps);
            boolean upToDate = false;
            boolean there = false;
            while (rs.next()) {
                there = true;
                upToDate = rs.getString("username").equals(username);
            }
            if (!there) {
                ps = sql.prepare("insert into bungeemail_uuids values(NULL, ?, ?)");
            } else if (!upToDate) {
                ps = sql.prepare("update bungeemail_uuids set username=? where uuid=?");
            }
            ps.setString(1, username);
            ps.setString(2, uuid.toString());
            sql.query(ps);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    public static class SQLMessage implements Message {
        private final String senderName;
        private final UUID senderUUID;
        private final UUID recipient;
        private final String message;
        private boolean read;
        private final long time;
        private final long id;

        public SQLMessage(long id, String senderName, UUID senderUUID, UUID recipient, String message, boolean read, long time) {
            this.id = id;
            this.time = time;
            this.read = read;
            this.message = message;
            this.recipient = recipient;
            this.senderUUID = senderUUID;
            this.senderName = senderName;
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
            return Long.hashCode(id);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SQLMessage && ((SQLMessage)other).getId() == getId();
        }
    }
}
