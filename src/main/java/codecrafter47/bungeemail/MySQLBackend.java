package codecrafter47.bungeemail;

import lib.PatPeter.SQLibrary.Database;
import lib.PatPeter.SQLibrary.MySQL;
import lombok.SneakyThrows;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by florian on 15.11.14.
 */
public class MySQLBackend implements IStorageBackend, Listener {

	BungeeMail plugin;
	Database sql;

	public MySQLBackend(BungeeMail plugin) {
		this.plugin = plugin;
		sql = new MySQL(plugin.getLogger(), "[BungeeMail/MySQL]", plugin.config.getString("mysql_hostname"),
				plugin.config.getInt("mysql_port"), plugin.config.getString("mysql_database"),
				plugin.config.getString("mysql_username"), plugin.config.getString("mysql_password"));
		try {
			sql.open();
			sql.query("CREATE TABLE IF NOT EXISTS bungeemail_mails (id int NOT NULL AUTO_INCREMENT,senderName varchar(20), senderUUID varchar(40), recipient varchar(40), `message` varchar(255), `read` boolean, `time` bigint, PRIMARY KEY (id))");
			sql.query("CREATE TABLE IF NOT EXISTS bungeemail_uuids (id int NOT NULL AUTO_INCREMENT,username varchar(20), uuid varchar(40),PRIMARY KEY (id))");
		} catch (SQLException e) {
			plugin.getLogger().warning("MySQL setup failed");
			throw new RuntimeException(e);
		}
		plugin.getProxy().getPluginManager().registerListener(plugin, this);
	}

	@SneakyThrows
	@Override public List<Message> getMessagesFor(UUID uuid, boolean onlyNew) {
		if(!sql.isOpen()){
			sql.open();
		}
		ResultSet rs;
		if(onlyNew)
			rs = sql.query("select * from bungeemail_mails where recipient='" + uuid + "' and `read`='0'");
		else
			rs = sql.query("select * from bungeemail_mails where recipient='" + uuid + "'");
		ArrayList<Message> messages = new ArrayList<>();
		while(rs.next()){
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
	}

	@SneakyThrows
	@Override public void saveMessage(Message message) {
		if(!sql.isOpen()){
			sql.open();
		}
		if(message instanceof SQLMessage){
			sql.query("update bungeemail_mails set senderName='" + message.getSenderName() + "', senderUUID='"+message.getSenderUUID()+"'" +
					", recipient='"+message.getRecipient()+"', message='"+escapeBadChars(message.getMessage())+"'" +
					", `read`='"+(message.isRead()?1:0)+"', time='"+message.getTime()+"' where id='" + message.hashCode() + "'");
		} else {
			sql.query("insert into bungeemail_mails values(NULL,'"+message.getSenderName()+"', '"
					+ message.getSenderUUID() + "', '" + message.getRecipient() + "', '"
					+ escapeBadChars(message.getMessage()) + "', '" + (message.isRead()?1:0) + "', '" + message.getTime() + "')");
		}
	}

	@SneakyThrows
	@Override public void markRead(Message message) {
		if(!sql.isOpen()){
			sql.open();
		}
		sql.query("update bungeemail_mails set `read`=1 where id='" + message.hashCode() + "'");
	}

	@SneakyThrows
	@Override public void delete(Message message) {
		if(!sql.isOpen()){
			sql.open();
		}
		sql.query("delete from bungeemail_mails where id='" + message.hashCode() + "'");
	}

	@SneakyThrows
	@Override public void delete(int id) {
		if(!sql.isOpen()){
			sql.open();
		}
		sql.query("delete from bungeemail_mails where id='" + id + "'");
	}

	@SneakyThrows
	@Override public UUID getUUIDForName(String name) {
		if(!sql.isOpen()){
			sql.open();
		}
		ResultSet rs = sql.query("select * from bungeemail_uuids where username='" + name + "'");
		while (rs.next()){
			return UUID.fromString(rs.getString("uuid"));
		}
		return null;
	}

	@SneakyThrows
	@Override public Collection<UUID> getAllKnownUUIDs() {
		if(!sql.isOpen()){
			sql.open();
		}
		HashSet<UUID> uuids = new HashSet<>();
		ResultSet rs = sql.query("select * from bungeemail_uuids");
		while (rs.next()){
			uuids.add(UUID.fromString(rs.getString("uuid")));
		}
		return uuids;
	}

	@SneakyThrows
	@Override public Collection<String> getKnownUsernames() {
		if(!sql.isOpen()){
			sql.open();
		}
		HashSet<String> strings = new HashSet<>();
		ResultSet rs = sql.query("select * from bungeemail_uuids");
		while (rs.next()){
			strings.add(rs.getString("username"));
		}
		return strings;
	}

	@EventHandler
	public void onPlayerJoin(final PostLoginEvent event){
		plugin.getProxy().getScheduler().schedule(plugin, new Runnable() {
			@SneakyThrows
			@Override public void run() {
				if(!sql.isOpen()){
					sql.open();
				}
				ResultSet rs = sql.query("select * from bungeemail_uuids where uuid='"+event.getPlayer().getUniqueId()+"'");
				boolean upToDate = false;
				boolean there = false;
				while (rs.next()){
					there = true;
					upToDate = rs.getString("username").equals(event.getPlayer().getName());
				}
				if(!there){
					sql.query("insert into bungeemail_uuids values(NULL,'"+event.getPlayer().getName()+"', '"+event.getPlayer().getUniqueId() + "')");
				} else if(!upToDate){
					sql.query("update bungeemail_uuids set username='"+event.getPlayer().getName()+"' where uuid='" + event.getPlayer().getUniqueId() + "'");
				}
			}
		}, 1, TimeUnit.MILLISECONDS);
	}

	private String escapeBadChars(String str){
		return str.replaceAll("[\\\\'\"]", "\\\\$0");
	}

	public static class SQLMessage extends Message{
		int id;

		public SQLMessage(String senderName, UUID senderUUID, UUID recipient, String message, boolean read, long time, int id) {
			super(senderName, senderUUID, recipient, message, read, time);
			this.id = id;
		}

		@Override public boolean equals(Object obj) {
			return obj instanceof SQLMessage && ((SQLMessage)obj).id == id;
		}

		@Override public int hashCode() {
			return id;
		}
	}
}
