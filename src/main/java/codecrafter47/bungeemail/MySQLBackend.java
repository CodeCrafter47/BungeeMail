package codecrafter47.bungeemail;

import com.google.common.base.Preconditions;
import org.apache.commons.dbcp2.*;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;

import javax.sql.DataSource;
import java.sql.*;
import java.sql.Statement;
import java.util.*;

public class MySQLBackend implements IStorageBackend {

    BungeeMail plugin;
    DataSource dataSource;

    public MySQLBackend(BungeeMail plugin) {
        this.plugin = plugin;
        setupDataSource(plugin);
        try (Connection connection = dataSource.getConnection()) {
            try(Statement statement = connection.createStatement()){
                statement.execute("CREATE TABLE IF NOT EXISTS bungeemail_mails (id int NOT NULL AUTO_INCREMENT,senderName varchar(20), senderUUID varchar(40), recipient varchar(40), `message` varchar(255), `read` boolean, `time` bigint, PRIMARY KEY (id))");
                statement.execute("DROP TABLE IF EXISTS bungeemail_uuids");
                statement.execute("CREATE TABLE IF NOT EXISTS bungeemail_uuids_v2 (username varchar(20) NOT NULL UNIQUE PRIMARY KEY, uuid varchar(40))");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setup failed");
            throw new RuntimeException(e);
        }
    }

    private void setupDataSource(BungeeMail plugin) {
        try {
            Class.forName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
        } catch (ClassNotFoundException var2) {
            plugin.getLogger().warning("MySQL DataSource class missing: " + var2.getMessage() + ".");
            throw new RuntimeException("Failed to connect to MySql database");
        }
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory("jdbc:mysql://" + plugin.config.getString("mysql_hostname") + ":" + plugin.config.getInt("mysql_port") + "/" + plugin.config.getString("mysql_database"), plugin.config.getString("mysql_username"), plugin.config.getString("mysql_password"));
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        GenericObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory);
        connectionPool.setTestOnBorrow(true);
        connectionPool.setTestOnCreate(true);
        poolableConnectionFactory.setPool(connectionPool);
        this.dataSource = new PoolingDataSource<>(connectionPool);
    }

    @Override
    public List<Message> getMessagesFor(UUID uuid, boolean onlyNew) throws StorageException {
        try (Connection connection = dataSource.getConnection()){
            String sql;
            if (onlyNew) {
                sql = "select * from bungeemail_mails where recipient=? and `read`='0'";
            } else {
                sql = "select * from bungeemail_mails where recipient=?";
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
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
                }
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public Message saveMessage(String senderName, UUID senderUUID, UUID recipient, String message, boolean read, long time) throws StorageException {
        try (Connection connection = dataSource.getConnection()){
            try (PreparedStatement ps = connection.prepareStatement("insert into bungeemail_mails values(NULL, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, senderName);
                ps.setString(2, senderUUID.toString());
                ps.setString(3, recipient.toString());
                ps.setString(4, message);
                ps.setBoolean(5, read);
                ps.setLong(6, time);
                int affectedRows = ps.executeUpdate();
                if(affectedRows == 1) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if(rs.next()){
                            long id = rs.getLong(1);
                            return new SQLMessage(id, senderName, senderUUID, recipient, message, read, time);
                        } else {
                            throw new StorageException("Saving mail failed. Generated key not available");
                        }
                    }
                } else {
                    throw new StorageException("Saving mail failed. " + affectedRows + " rows inserted. But should be 1");
                }
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public int saveMessageToAll(String senderName, UUID senderUUID, String message, boolean read, long time) throws StorageException {
        try (Connection connection = dataSource.getConnection()){
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO bungeemail_mails (senderName, senderUUID, recipient, message, `read`, `time`) SELECT DISTINCT ?, ?, uuid, ?, ?, ? FROM bungeemail_uuids_v2")) {
                ps.setString(1, senderName);
                ps.setString(2, senderUUID.toString());
                ps.setString(3, message);
                ps.setBoolean(4, read);
                ps.setLong(5, time);
                int affectedRows = ps.executeUpdate();
                return affectedRows;
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void markRead(Message message) throws StorageException {
        Preconditions.checkArgument(message instanceof SQLMessage);
        try (Connection connection = dataSource.getConnection()){
            try(PreparedStatement ps = connection.prepareStatement("update bungeemail_mails set `read`=1 where id=?")) {
                ps.setLong(1, message.hashCode());
                if(ps.executeUpdate() == 0){
                    throw new StorageException("Tried to read non-existent mail");
                }
                ((SQLMessage) message).setRead(true);
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void delete(Message message) throws StorageException {
        delete(message.getId(), message.getRecipient());
    }

    @Override
    public boolean delete(long id, UUID recipient) throws StorageException {
        try (Connection connection = dataSource.getConnection()){
            try (PreparedStatement ps = connection.prepareStatement("delete from bungeemail_mails where id=? and recipient=?")) {
                ps.setLong(1, id);
                ps.setString(2, recipient.toString());
                if(ps.executeUpdate() == 0){
                    throw new StorageException("Tried to delete non-existent mail");
                }
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
        return true;
    }

    @Override
    public void deleteOlder(long time, boolean deleteUnread) throws StorageException {
        try (Connection connection = dataSource.getConnection()){
            String sql;
            if (deleteUnread) {
                sql = "delete from bungeemail_mails where time < ?";
            } else {
                sql = "delete from bungeemail_mails where time < ? and `read`=1";
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, time);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public UUID getUUIDForName(String name) throws StorageException {
        if ("Console".equals(name)) {
            return BungeeMail.CONSOLE_UUID;
        }
        try (Connection connection = dataSource.getConnection()){
            try (PreparedStatement ps = connection.prepareStatement("select uuid from bungeemail_uuids_v2 where username=?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("uuid"));
                    }
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public Collection<UUID> getAllKnownUUIDs() throws StorageException {
        try (Connection connection = dataSource.getConnection()){
            try(Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("select uuid from bungeemail_uuids_v2")) {
                    Collection<UUID> uuids = new ArrayList<>();
                    while (rs.next()) {
                        uuids.add(UUID.fromString(rs.getString("uuid")));
                    }
                    return Collections.unmodifiableCollection(uuids);
                }
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public Collection<String> getKnownUsernames() throws StorageException {
        try (Connection connection = dataSource.getConnection()){
            try(Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("select username from bungeemail_uuids_v2")) {
                    Collection<String> names = new ArrayList<>();
                    while (rs.next()) {
                        names.add(rs.getString("username"));
                    }
                    return Collections.unmodifiableCollection(names);
                }
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void updateUserEntry(final UUID uuid, final String username) throws StorageException {
        try (Connection connection = dataSource.getConnection()){
            try(PreparedStatement ps = connection.prepareStatement("insert into bungeemail_uuids_v2 (username, uuid) values(?, ?) ON DUPLICATE KEY UPDATE uuid=VALUES(uuid)")){
                ps.setString(1, username);
                ps.setString(2, uuid.toString());
                ps.execute();
            }
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
            return Long.valueOf(id).hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SQLMessage && ((SQLMessage) other).getId() == getId();
        }
    }
}
