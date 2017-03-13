package codecrafter47.bungeemail;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

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
            commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("help")));
            return;
        }
        switch (args[0]) {
            case "view":
            case "list":
            case "read":
                int start = 1;
                if (args.length >= 2) start = Integer.valueOf(args[1]);
                try {
                    plugin.listMessages((ProxiedPlayer) commandSender, start, true, false);
                } catch (StorageException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to show mails to player", e);
                    commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("commandError", "&cAn error occurred while processing your command: %error%").replace("%error%", e.getMessage())));
                }
                return;
            case "listall":
                start = 1;
                if (args.length >= 2) start = Integer.valueOf(args[1]);
                try {
                    plugin.listMessages((ProxiedPlayer) commandSender, start, true, true);
                } catch (StorageException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to show mails to player", e);
                    commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("commandError", "&cAn error occurred while processing your command: %error%").replace("%error%", e.getMessage())));
                }
                return;
            case "sendall":
                if (!commandSender.hasPermission("bungeemail.sendall")) {
                    commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("noPermission", "&cYou. Don't. Have. Permission.")));
                    return;
                }
                String text = "";
                for (int i = 1; i < args.length; i++) {
                    text += args[i] + " ";
                }
                plugin.sendMailToAll((ProxiedPlayer) commandSender, text);
                return;
            case "reload":
                if (!commandSender.hasPermission("bungeemail.admin")) {
                    commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("noPermission", "&cYou. Don't. Have. Permission.")));
                    return;
                }
                plugin.reload();
                return;
            case "send":
                if (args.length < 3) {
                    commandSender.sendMessage(plugin.getChatParser().parse("&cUsage: &f[suggest=/mail send ]/mail send <player> <message>[/suggest]"));
                    return;
                }
                String target = args[1];
                text = "";
                for (int i = 2; i < args.length; i++) {
                    text += args[i] + " ";
                }
                plugin.sendMail((ProxiedPlayer) commandSender, target, text);
                return;
            case "help":
                commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("help")));
                return;
            case "del":
                if (args.length < 2) {
                    commandSender.sendMessage(plugin.getChatParser().parse("/mail del <all|read|#>"));
                    return;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    try {
                        for (Message msg : plugin.getStorage().getMessagesFor(((ProxiedPlayer) commandSender).getUniqueId(), false))
                            plugin.getStorage().delete(msg);
                        commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("deletedAll", "&aYou deleted all mails.")));
                    } catch (StorageException e) {
                        plugin.getLogger().log(Level.SEVERE, "Unable to process user command \"/mail del all\"", e);
                        commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("commandError", "&cAn error occurred while processing your command: %error%").replace("%error%", e.getMessage())));
                    }
                } else if (args[1].equalsIgnoreCase("read")) {
                    try {
                        for (Message msg : plugin.getStorage().getMessagesFor(((ProxiedPlayer) commandSender).getUniqueId(), true))
                            plugin.getStorage().delete(msg);
                        commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("deletedRead", "&aYou deleted all read mails.")));
                    } catch (StorageException e) {
                        plugin.getLogger().log(Level.SEVERE, "Unable to process user command \"/mail del read\"", e);
                        commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("commandError", "&cAn error occurred while processing your command: %error%").replace("%error%", e.getMessage())));
                    }
                } else {
                    try {
                        long id = Long.valueOf(args[1]);
                        plugin.getStorage().delete(id, ((ProxiedPlayer) commandSender).getUniqueId());
                        commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("deletedSingle", "&aYou deleted 1 message.")));
                    } catch (StorageException | NumberFormatException e) {
                        plugin.getLogger().log(Level.SEVERE, "Unable to process user command \"/mail del " + args[1] + "\"", e);
                        commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("commandError", "&cAn error occurred while processing your command: %error%").replace("%error%", e.getMessage())));
                    }
                }
                return;
            default:
                // send mail
                target = args[0];
                text = "";
                for (int i = 1; i < args.length; i++) {
                    text += args[i] + " ";
                }
                plugin.sendMail((ProxiedPlayer) commandSender, target, text);
        }
    }

}
