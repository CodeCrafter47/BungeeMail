package codecrafter47.bungeemail;

import codecrafter47.util.chat.ChatUtil;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.SneakyThrows;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class BungeeMail extends Plugin {
    
    public static final UUID CONSOLE_UUID = new UUID(0, 0);
    
    Configuration config;
    Messages messages;

    static BungeeMail instance;

    @Getter
    private IStorageBackend storage;

    @SneakyThrows
    @Override
    public void onEnable() {
        // enable it
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdir()) {
                getLogger().severe("Failed to create plugin data folder, plugin won't be enabled");
                return;
            }
        }

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            Files.copy(getResourceAsStream("config.yml"), file.toPath());
        }

        Configuration defaultConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(getResourceAsStream("config.yml"));

        config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file, defaultConfig);

        if (!config.getBoolean("useMySQL")) {
            final FlatFileBackend fileBackend = new FlatFileBackend(this);
            if (!fileBackend.readData()) {
                getLogger().log(Level.SEVERE, "Failed to load mail data from file, plugin won't be enabled");
                return;
            }
            // schedule saving
            getProxy().getScheduler().schedule(this, new Runnable() {
                @Override
                public void run() {
                    fileBackend.saveData();
                }
            }, 2, 2, TimeUnit.MINUTES);
            storage = fileBackend;
        } else {
            storage = new MySQLBackend(this);
        }

        messages = new Messages(config);
        instance = this;

        getProxy().getPluginManager().registerCommand(this, new MailCommand(config.getString("mail_command"), "bungeemail.use", this));
        getProxy().getPluginManager().registerListener(this, new PlayerListener(this));

        if (config.getBoolean("cleanup_enabled", false)) {
            getProxy().getScheduler().schedule(this, new Runnable() {
                @Override
                public void run() {
                    try {
                        storage.deleteOlder(System.currentTimeMillis() - (60L * 60L * 24L * config.getLong("cleanup_threshold", 7L)), false);
                    } catch (StorageException e) {
                        getLogger().log(Level.WARNING, "Automatic database cleanup failed", e);
                    }
                }
            }, 1, 120, TimeUnit.MINUTES);
        }
    }

    @Override
    public void onDisable() {
        if (storage != null && storage instanceof FlatFileBackend) {
            ((FlatFileBackend) storage).saveData();
        }
    }

    @SneakyThrows
    void reload() {
        File file = new File(getDataFolder(), "config.yml");
        config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        messages = new Messages(config);
    }

    public void listMessages(CommandSender sender, int start, boolean listIfNotAvailable, boolean listReadMessages) throws StorageException{
        String noMessagesTemplate = listReadMessages ? messages.noMessages : messages.noNewMessages;
        String headerTemplate = listReadMessages ? messages.listallHeader : messages.listHeader;
        String oldMessageTemplate = messages.oldMessage;
        String newMessageTemplate = messages.newMessage;
        String footerTemplate = listReadMessages ? messages.listallFooter : messages.listFooter;

        List<Message> messages;
        UUID senderUUID = sender instanceof ProxiedPlayer ? ((ProxiedPlayer) sender).getUniqueId() : CONSOLE_UUID;
        try {
            messages = getStorage().getMessagesFor(senderUUID, !listReadMessages);
        } catch (StorageException e) {
            getLogger().log(Level.SEVERE, "Unable to get mails for " + sender.getName() + " from storage", e);
            throw e;
        }
        if (messages.isEmpty() && listIfNotAvailable) {
            sender.sendMessage(ChatUtil.parseBBCode(noMessagesTemplate));
        }
        if (messages.isEmpty()) return;
        if (listReadMessages) {
            messages = Lists.reverse(messages);
        }
        if (start >= messages.size()) start = 1;
        int i = 1;
        int end = start + 9;
        if (end >= messages.size()) end = messages.size();
        List<BaseComponent> output = new ArrayList<>();
        output.addAll(Arrays.asList(ChatUtil.parseBBCode(headerTemplate.
                replace("%start%", "" + start).replace("%end%", "" + end).
                replace("%max%", "" + messages.size()).replace("%list%", listReadMessages ? "listall" : "list").
                replace("%next%", "" + (end + 1)).replace("%visible%", messages.size() > 10 ? "" + 10 : ("" + messages.size())))));
        for (Message message : messages) {
            if (i >= start && i < start + 10) {
                output.add(new TextComponent("\n"));
                output.addAll(Arrays.asList(ChatUtil.parseBBCode((message.isRead() ? oldMessageTemplate : newMessageTemplate).
                        replace("%sender%", "[nobbcode]" + message.getSenderName() + "[/nobbcode]").
                        replace("%time%", formatTime(message.getTime())).
                        replace("%id%", "" + message.getId()).
                        replace("%message%", message.getMessage()))));
                try {
                    storage.markRead(message);
                } catch (StorageException e) {
                    getLogger().log(Level.SEVERE, "Failed to mark mail as read", e);
                }
            }
            i++;
        }
        if (!Strings.isNullOrEmpty(footerTemplate)) {
            output.add(new TextComponent("\n"));
            output.addAll(Arrays.asList(ChatUtil.parseBBCode(footerTemplate.
                    replace("%start%", "" + start).replace("%end%", "" + end).
                    replace("%max%", "" + messages.size()).replace("%list%", listReadMessages ? "listall" : "list").
                    replace("%next%", "" + (end + 1)).replace("%visible%", messages.size() > 10 ? "" + 10 : ("" + messages.size())))));
        }
        sender.sendMessage(output.toArray(new BaseComponent[0]));
    }

    public void showLoginInfo(ProxiedPlayer player) {
        String loginNewMailsTemplate = messages.loginNewMails;
        try {
            List<Message> messages = getStorage().getMessagesFor(player.getUniqueId(), true);
            if (!messages.isEmpty()) {
                player.sendMessage(ChatUtil.parseBBCode(loginNewMailsTemplate.replace("%num%", "" + messages.size())));
            }
        } catch (StorageException e) {
            getLogger().log(Level.WARNING, "Failed to show mail notification to " + player.getName(), e);
        }
    }

    private String formatTime(long time) {
        return new SimpleDateFormat("hh:mm:ss").format(new Date(time));
    }

    public void sendMail(CommandSender sender, String target, String text) {
        if (text.trim().isEmpty()) {
            sender.sendMessage(ChatUtil.parseBBCode(messages.emptyMail));
            return;
        }
        long time = System.currentTimeMillis();
        UUID senderUUID = sender instanceof ProxiedPlayer ? ((ProxiedPlayer) sender).getUniqueId() : CONSOLE_UUID;
        UUID targetUUID = null;
        try {
            targetUUID = storage.getUUIDForName(target);
        } catch (StorageException e) {
            getLogger().log(Level.WARNING, "Unable to do a name to uuid lookup", e);
        }
        if (targetUUID == null) {
            sender.sendMessage(ChatUtil.parseBBCode(messages.unknownTarget));
            return;
        }
        try {
            String message = ChatUtil.stripBBCode(text);
            message = message.replaceAll("(?<link>(?:(https?)://)?([-\\w_\\.]{2,}\\.[a-z]{2,4})(/\\S*)?)", "[url]${link}[/url]");
            storage.saveMessage(sender.getName(), senderUUID, targetUUID, message, false, time);
            sender.sendMessage(ChatUtil.parseBBCode(messages.messageSent));
            if (getProxy().getPlayer(targetUUID) != null) {
                getProxy().getPlayer(targetUUID).sendMessage(ChatUtil.parseBBCode(messages.receivedNewMessage));
            } else if (targetUUID.equals(CONSOLE_UUID)) {
                getProxy().getConsole().sendMessage(ChatUtil.parseBBCode(messages.receivedNewMessage));
            }
        } catch (StorageException e) {
            getLogger().log(Level.WARNING, "Unable to save mail", e);
            sender.sendMessage(ChatUtil.parseBBCode(messages.commandError.replace("%error%", e.getMessage())));
        }
    }

    public void sendMailToAll(CommandSender sender, String text) {
        if (text.trim().isEmpty()) {
            sender.sendMessage(ChatUtil.parseBBCode(messages.emptyMail));
            return;
        }
        long time = System.currentTimeMillis();
        UUID senderUUID = sender instanceof ProxiedPlayer ? ((ProxiedPlayer) sender).getUniqueId() : CONSOLE_UUID;
        Iterable<UUID> targets;
        try {
             targets = storage.getAllKnownUUIDs();
        } catch (StorageException e) {
            getLogger().log(Level.WARNING, "Unable to send mail to all players", e);
            sender.sendMessage(ChatUtil.parseBBCode(messages.commandError.replace("%error%", e.getMessage())));
            return;
        }
        targets = Iterables.concat(targets, Collections.singletonList(CONSOLE_UUID));
        text = ChatUtil.stripBBCode(text);
        text = text.replaceAll("(?<link>(?:(https?)://)?([-\\w_\\.]{2,}\\.[a-z]{2,4})(/\\S*)?)", "[url]${link}[/url]");
        int count = 0;
        for (UUID targetUUID : targets) {
            if (targetUUID.equals(senderUUID)) continue;
            try {
                storage.saveMessage(sender.getName(), senderUUID, targetUUID, text, false, time);
                if (getProxy().getPlayer(targetUUID) != null) {
                    getProxy().getPlayer(targetUUID).sendMessage(ChatUtil.parseBBCode(messages.receivedNewMessage));
                }
                count++;
            } catch (StorageException e) {
                getLogger().log(Level.WARNING, "Unable to save mail", e);
            }
        }
        if (!sender.equals(getProxy().getConsole())) {
            getProxy().getConsole().sendMessage(ChatUtil.parseBBCode(messages.receivedNewMessage));
        }
        sender.sendMessage(ChatUtil.parseBBCode(messages.messageSentToAll.replaceAll("%num%", Integer.toString(count))));
    }
}
