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
import java.util.concurrent.TimeUnit;

/**
 * Created by florian on 15.11.14.
 */
public class BungeeMail extends Plugin {

    Configuration config;

    static BungeeMail instance;

    @Getter
    private IStorageBackend storage;

    @SneakyThrows
    @Override
    public void onEnable() {
        // enable it
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            Files.copy(getResourceAsStream("config.yml"), file.toPath());
        }

        config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

        if (!config.getBoolean("useMySQL")) {
            storage = new FlatFileBackend(this);
        } else {
            storage = new MySQLBackend(this);
        }

        instance = this;

        getProxy().getPluginManager().registerCommand(this, new MailCommand("mail", "bungeemail.use", this));
        getProxy().getPluginManager().registerListener(this, new PlayerListener(this));

        if(config.getBoolean("cleanup_enabled", false)){
            getProxy().getScheduler().schedule(this, new Runnable() {
                @Override
                public void run() {
                    storage.deleteOlder(System.currentTimeMillis() - (60L * 60L * 24L * config.getLong("cleanup_threshold", 7L)), false);
                }
            }, 1, 120, TimeUnit.MINUTES);
        }
    }

    public void listMessages(ProxiedPlayer player, int start, boolean listIfNotAvailable, boolean listReadMessages) {
        List<Message> messages = getStorage().getMessagesFor(player.getUniqueId(), !listReadMessages);
        if (messages.isEmpty() && listIfNotAvailable) {
            player.sendMessage(ChatParser.parse(config.getString("noNewMessages")));
        }
        if (messages.isEmpty()) return;
        if (listReadMessages)
            messages = Lists.reverse(messages);
        if (start >= messages.size()) start = 1;
        int i = 1;
        int end = start + 9;
        if (end >= messages.size()) end = messages.size();
        player.sendMessage(ChatParser.parse(config.getString(listReadMessages ? "listallHeader" : "listHeader").
                replace("%start%", "" + start).replace("%end%", "" + end).
                replace("%max%", "" + messages.size()).replace("%list%", listReadMessages ? "listall" : "list").
                replace("%next%", "" + (end + 1)).replace("%visible%", messages.size() > 10 ? "" + 10 : ("" + messages.size()))));
        for (Message message : messages) {
            if (i >= start && i < start + 10) {
                player.sendMessage(ChatParser.parse(config.getString(message.isRead() ? "oldMessage" : "newMessage").
                        replace("%sender%", "[nobbcode]" + message.getSenderName() + "[/nobbcode]").
                        replace("%time%", formatTime(message.getTime())).
                        replace("%id%", "" + message.hashCode()).
                        replace("%message%", message.getMessage())));
                storage.markRead(message);
            }
            i++;
        }
    }

    public void showLoginInfo(ProxiedPlayer player) {
        List<Message> messages = getStorage().getMessagesFor(player.getUniqueId(), true);
        if (!messages.isEmpty()) {
            player.sendMessage(ChatParser.parse(config.getString("loginNewMails",
                    "&aYou have %num% new mails. Type [i][command]/mail view[/command][/i] to read them.").replace("%num%", "" + messages.size())));
        }
    }

    private String formatTime(long time) {
        return new SimpleDateFormat("hh:mm:ss").format(new Date(time));
    }

    public void sendMail(ProxiedPlayer sender, String target, String text) {
        long time = System.currentTimeMillis();
        UUID targetUUID = storage.getUUIDForName(target);
        if (targetUUID == null) {
            sender.sendMessage(ChatParser.parse(config.getString("unknownTarget")));
            return;
        }
        Message message = new Message(sender.getName(), sender.getUniqueId(), targetUUID, ChatParser.stripBBCode(text), false, time);
        storage.saveMessage(message);
        sender.sendMessage(ChatParser.parse(config.getString("messageSent")));
        if (getProxy().getPlayer(targetUUID) != null) {
            getProxy().getPlayer(targetUUID).sendMessage(ChatParser.parse(config.getString("receivedNewMessage")));
        }
    }

    public void sendMailToAll(ProxiedPlayer sender, String text) {
        long time = System.currentTimeMillis();
        Collection<UUID> targets = storage.getAllKnownUUIDs();
        text = ChatParser.stripBBCode(text);
        for (UUID targetUUID : targets) {
            if (targetUUID.equals(sender.getUniqueId())) continue;
            Message message = new Message(sender.getName(), sender.getUniqueId(), targetUUID, text, false, time);
            storage.saveMessage(message);
            if (getProxy().getPlayer(targetUUID) != null) {
                getProxy().getPlayer(targetUUID).sendMessage(ChatParser.parse(config.getString("receivedNewMessage")));
            }
        }
        sender.sendMessage(ChatParser.parse(config.getString("messageSentToAll").replaceAll("%num%", "" + (targets.size() - 1))));
    }
}
