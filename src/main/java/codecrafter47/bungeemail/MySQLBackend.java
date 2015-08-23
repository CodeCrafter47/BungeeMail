package codecrafter47.bungeemail;

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
                int id = rs.getInt("id");
                messages.add(new SQLMessage(senderName, senderUUID, recipient, message, read, time, id));
            }
            return messages;
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void saveMessage(Message message) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            if (message instanceof SQLMessage) {
                PreparedStatement ps = sql.prepare("update bungeemail_mails set senderName=?, senderUUID=?, recipient=?, message=?, `read`=?, time=? where id=?");
                ps.setString(1, message.getSenderName());
                ps.setString(2, message.getSenderUUID().toString());
                ps.setString(3, message.getRecipient().toString());
                ps.setString(4, message.getMessage());
                ps.setBoolean(5, message.isRead());
                ps.setLong(6, message.getTime());
                ps.setLong(7, message.hashCode());
                sql.query(ps);
            } else {
                PreparedStatement ps = sql.prepare("insert into bungeemail_mails values(NULL, ?, ?, ?, ?, ?, ?)");
                ps.setString(1, message.getSenderName());
                ps.setString(2, message.getSenderUUID().toString());
                ps.setString(3, message.getRecipient().toString());
                ps.setString(4, message.getMessage());
                ps.setBoolean(5, message.isRead());
                ps.setLong(6, message.getTime());
                sql.query(ps);
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void markRead(Message message) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps = sql.prepare("update bungeemail_mails set `read`=1 where id=?");
            ps.setLong(1, message.hashCode());
            sql.query(ps);
            message.setRead(true);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void delete(Message message) throws StorageException {
        try {
            if (!sql.isOpen()) {
                sql.open();
            }
            PreparedStatement ps = sql.prepare("delete from bungeemail_mails where id=?");
            ps.setLong(1, message.hashCode());
            sql.query(ps);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void delete(int id) throws StorageException {
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

    public static class SQLMessage extends Message {
        int id;

        public SQLMessage(String senderName, UUID senderUUID, UUID recipient, String message, boolean read, long time, int id) {
            super(senderName, senderUUID, recipient, message, read, time);
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SQLMessage && ((SQLMessage) obj).id == id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }
}
