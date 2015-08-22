package codecrafter47.bungeemail;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

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
                plugin.listMessages((ProxiedPlayer) commandSender, start, true, false);
                return;
            case "listall":
                start = 1;
                if (args.length >= 2) start = Integer.valueOf(args[1]);
                plugin.listMessages((ProxiedPlayer) commandSender, start, true, true);
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
            case "send":
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
                    commandSender.sendMessage("/mail del <all|read|#>");
                    return;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    for (Message msg : plugin.getStorage().getMessagesFor(((ProxiedPlayer) commandSender).getUniqueId(), false))
                        plugin.getStorage().delete(msg);
                    commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("deletedAll", "&aYou deleted all mails.")));
                } else if (args[1].equalsIgnoreCase("read")) {
                    for (Message msg : plugin.getStorage().getMessagesFor(((ProxiedPlayer) commandSender).getUniqueId(), true))
                        plugin.getStorage().delete(msg);
                    commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("deletedRead", "&aYou deleted all read mails.")));
                } else {
                    int id = Integer.valueOf(args[1]);
                    plugin.getStorage().delete(id);
                    commandSender.sendMessage(plugin.getChatParser().parse(plugin.config.getString("deletedSingle", "&aYou deleted 1 message.")));
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
                return;
        }
    }

}
