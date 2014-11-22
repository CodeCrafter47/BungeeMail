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

	@Override public void execute(CommandSender commandSender, String[] args) {
		if(args.length < 1){
			printHelp(commandSender);
			return;
		}
		if(plugin.getProxy().getPlayer(args[0]) != null){
			// send mail
			String target = args[0];
			String text = "";
			for(int i=1; i<args.length; i++){
				text += args[i] + " ";
			}
			plugin.sendMail((ProxiedPlayer)commandSender, target, text);
			return;
		}
		switch (args[0]){
			case "view":
			case "list":
				int start = 1;
				if(args.length >= 2)start = Integer.valueOf(args[1]);
				plugin.listMessages((ProxiedPlayer)commandSender, start, true, false);
				return;
			case "listall":
				start = 1;
				if(args.length >= 2)start = Integer.valueOf(args[1]);
				plugin.listMessages((ProxiedPlayer)commandSender, start, true, true);
				return;
			case "sendall":
				if(!commandSender.hasPermission("bungeemail.sendall")){
					commandSender.sendMessage(ChatUtil.parseString(plugin.config.getString("noPermission", "&cYou. Don't. Have. Permission.")));
					return;
				}
				String text = "";
				for(int i=1; i<args.length; i++){
					text += args[i] + " ";
				}
				plugin.sendMailToAll((ProxiedPlayer)commandSender, text);
				return;
			case "help":
			default:
				printHelp(commandSender);
		}
	}

	private void printHelp(CommandSender sender) {
		for(String s: plugin.config.getString("help").split("%newline%")){
			sender.sendMessage(ChatUtil.parseString(s));
		}
	}
}
