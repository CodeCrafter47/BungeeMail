package codecrafter47.bungeemail;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.SneakyThrows;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * Created by florian on 15.11.14.
 */
public class BungeeMail extends Plugin {

	Configuration config;

	static BungeeMail instance;

	@Getter
	private IStorageBackend storage;

	@SneakyThrows
	@Override public void onEnable() {
		// enable it
		if (!getDataFolder().exists()) {
			getDataFolder().mkdir();
		}

		File file = new File(getDataFolder(), "config.yml");

		if (!file.exists()) {
			Files.copy(getResourceAsStream("config.yml"), file.toPath());
		}

		config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

		if(!config.getBoolean("useMySQL")){
			storage = new FlatFileBackend(this);
		} else {
			storage = new MySQLBackend(this);
		}

		instance = this;

		getProxy().getPluginManager().registerCommand(this, new MailCommand("mail", "bungeemail.use", this));
		getProxy().getPluginManager().registerListener(this, new PlayerListener(this));
	}

	public void listMessages(ProxiedPlayer player, int start, boolean listIfNotAvailable, boolean listReadMessages) {
		List<Message> messages = getStorage().getMessagesFor(player.getUniqueId(), !listReadMessages);
		if(messages.isEmpty() && listIfNotAvailable){
			player.sendMessage(ChatUtil.parseString(config.getString("noNewMessages")));
		}
		if(messages.isEmpty())return;
		if(listReadMessages)
			messages = Lists.reverse(messages);
		if(start >= messages.size())start = 1;
		int i = 1;
		int end = start+9;
		if(end >= messages.size())end = messages.size();
		player.sendMessage(ChatUtil.parseString(config.getString(listReadMessages ? "listallHeader" : "listHeader").
				replaceAll("%start%", "" + start).replaceAll("%end%", "" + end).
				replaceAll("%max%", "" + messages.size()).replaceAll("%list%", listReadMessages ? "listall" : "list").
				replaceAll("%next%", "" + (end + 1)).replaceAll("%visible%", messages.size() > 10 ? "" + 10 : ("" + messages.size()))));
		for(Message message: messages){
			if(i >= start && i < start + 10) {
				player.sendMessage(ChatUtil.parseString(config.getString(message.isRead() ? "oldMessage" : "newMessage").
						replaceAll("%sender%", ChatUtil.escapeSpecialChars(message.getSenderName())).
						replaceAll("%time%", formatTime(message.getTime())).
						replaceAll("%id%", "" + message.hashCode()).
						replaceAll("%message%", Matcher.quoteReplacement(message.getMessage()))));
				storage.markRead(message);
			}
			i++;
		}
	}

	private String formatTime(long time) {
		return new SimpleDateFormat("hh:mm:ss").format(new Date(time));
	}

	public void sendMail(ProxiedPlayer sender, String target, String text) {
		long time = System.currentTimeMillis();
		UUID targetUUID = storage.getUUIDForName(target);
		if(targetUUID == null){
			sender.sendMessage(ChatUtil.parseString(config.getString("unknownTarget")));
			return;
		}
		Message message = new Message(sender.getName(), sender.getUniqueId(), targetUUID, text, false, time);
		storage.saveMessage(message);
		sender.sendMessage(ChatUtil.parseString(config.getString("messageSent")));
		if(getProxy().getPlayer(targetUUID) != null){
			getProxy().getPlayer(targetUUID).sendMessage(ChatUtil.parseString(config.getString("receivedNewMessage")));
		}
	}

	public void sendMailToAll(ProxiedPlayer sender, String text) {
		long time = System.currentTimeMillis();
		Collection<UUID> targets = storage.getAllKnownUUIDs();
		for(UUID targetUUID: targets) {
			if(targetUUID.equals(sender.getUniqueId()))continue;
			Message message = new Message(sender.getName(), sender.getUniqueId(), targetUUID, text, false, time);
			storage.saveMessage(message);
			if (getProxy().getPlayer(targetUUID) != null) {
				getProxy().getPlayer(targetUUID).sendMessage(ChatUtil.parseString(config.getString("receivedNewMessage")));
			}
		}
		sender.sendMessage(ChatUtil.parseString(config.getString("messageSentToAll").replaceAll("%num%", "" + (targets.size() - 1))));
	}
}
