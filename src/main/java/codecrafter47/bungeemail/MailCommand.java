package codecrafter47.bungeemail;

import codecrafter47.util.chat.ChatUtil;
import com.google.common.base.Joiner;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public class MailCommand extends Command {

    private BungeeMail plugin;

    public MailCommand(String name, String permission, BungeeMail plugin) {
        super(name, permission);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender commandSender, String[] args) {
        if (args.length < 1) {
            commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.help));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "view":
            case "list":
            case "read":
                int start = 1;
                if (args.length >= 2) {
                    try {
                        start = Integer.valueOf(args[1]);
                    } catch (NumberFormatException e) {
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.wrongSyntaxList));
                        return;
                    }
                }
                try {
                    plugin.listMessages(commandSender, start, true, false);
                } catch (StorageException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to show mails to player", e);
                    commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.commandError.replace("%error%", e.getMessage())));
                }
                return;
            case "listall":
                start = 1;
                if (args.length >= 2) {
                    try {
                        start = Integer.valueOf(args[1]);
                    } catch (NumberFormatException e) {
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.wrongSyntaxListall));
                        return;
                    }
                }
                try {
                    plugin.listMessages(commandSender, start, true, true);
                } catch (StorageException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to show mails to player", e);
                    commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.commandError.replace("%error%", e.getMessage())));
                }
                return;
            case "sendall":
                if (!commandSender.hasPermission(Permissions.COMMAND_SENDALL)) {
                    commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.noPermission));
                    return;
                }
                String text = "";
                for (int i = 1; i < args.length; i++) {
                    text += args[i] + " ";
                }
                plugin.sendMailToAll(commandSender, text);
                return;
            case "reload":
                if (!commandSender.hasPermission(Permissions.COMMAND_ADMIN)) {
                    commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.noPermission));
                    return;
                }
                plugin.reload();
                List<String> config_options_reload = new ArrayList<>();
                for (String option : BungeeMail.CONFIG_OPTIONS_THAT_NEED_RELOAD) {
                    if (!Objects.equals(plugin.startupConfig.get(option), plugin.config.get(option))) {
                        config_options_reload.add(option);
                    }
                }
                if (config_options_reload.isEmpty()) {
                    commandSender.sendMessage(ChatUtil.parseBBCode("&aBungeeMail: &fReload Successfull"));
                } else {
                    commandSender.sendMessage(ChatUtil.parseBBCode("&aBungeeMail: &fA restart is required for your changes to the following options to take effect: " + Joiner.on(", ").join(config_options_reload)));
                }
                return;
            case "send":
                if (!commandSender.hasPermission(Permissions.COMMAND_SEND)) {
                    commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.noPermission));
                    return;
                }
                if (args.length < 2) {
                    commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.wrongSyntaxSend));
                    return;
                }
                String target = args[1];
                text = "";
                for (int i = 2; i < args.length; i++) {
                    text += args[i] + " ";
                }
                plugin.sendMail(commandSender, target, text);
                return;
            case "help":
                commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.help));
                return;
            case "del":
                if (args.length < 2) {
                    commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.wrongSyntaxDelete));
                    return;
                }
                UUID senderUUID = commandSender instanceof ProxiedPlayer ? ((ProxiedPlayer) commandSender).getUniqueId() : BungeeMail.CONSOLE_UUID;
                if (args[1].equalsIgnoreCase("all")) {
                    try {
                        for (Message msg : plugin.getStorage().getMessagesFor(senderUUID, false))
                            plugin.getStorage().delete(msg);
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.deletedAll));
                    } catch (StorageException e) {
                        plugin.getLogger().log(Level.SEVERE, "Unable to process user command \"/mail del all\"", e);
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.commandError.replace("%error%", e.getMessage())));
                    }
                } else if (args[1].equalsIgnoreCase("read")) {
                    try {
                        for (Message msg : plugin.getStorage().getMessagesFor(senderUUID, true))
                            plugin.getStorage().delete(msg);
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.deletedRead));
                    } catch (StorageException e) {
                        plugin.getLogger().log(Level.SEVERE, "Unable to process user command \"/mail del read\"", e);
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.commandError.replace("%error%", e.getMessage())));
                    }
                } else {
                    try {
                        long id = Long.valueOf(args[1]);
                        plugin.getStorage().delete(id, senderUUID);
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.deletedSingle));
                    } catch (NumberFormatException e) {
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.wrongSyntaxDelete));
                    } catch (StorageException e) {
                        plugin.getLogger().log(Level.SEVERE, "Unable to process user command \"/mail del " + args[1] + "\"", e);
                        commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.commandError.replace("%error%", e.getMessage())));
                    }
                }
                return;
            default:
                if (!commandSender.hasPermission(Permissions.COMMAND_SEND)) {
                    commandSender.sendMessage(ChatUtil.parseBBCode(plugin.messages.help));
                    return;
                }
                // send mail
                target = args[0];
                text = "";
                for (int i = 1; i < args.length; i++) {
                    text += args[i] + " ";
                }
                plugin.sendMail(commandSender, target, text);
        }
    }

}
