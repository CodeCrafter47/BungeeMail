package codecrafter47.bungeemail;

import net.md_5.bungee.config.Configuration;

public class Messages {

    public final String noMessages;
    public final String noNewMessages;
    public final String listallHeader;
    public final String listHeader;
    public final String oldMessage;
    public final String newMessage;
    public final String listFooter;
    public final String listallFooter;
    public final String loginNewMails;
    public final String emptyMail;
    public final String unknownTarget;
    public final String messageSent;
    public final String receivedNewMessage;
    public final String commandError;
    public final String messageSentToAll;
    public final String help;
    public final String wrongSyntaxList;
    public final String wrongSyntaxListall;
    public final String wrongSyntaxSend;
    public final String wrongSyntaxDelete;
    public final String noPermission;
    public final String deletedAll;
    public final String deletedRead;
    public final String deletedSingle;

    public Messages(Configuration config) {
        noMessages = config.getString("noMessages");
        noNewMessages = config.getString("noNewMessages");
        listallHeader = config.getString("listallHeader");
        listHeader = config.getString("listHeader");
        oldMessage = config.getString("oldMessage");
        newMessage = config.getString("newMessage");
        listFooter = config.getString("listFooter");
        listallFooter = config.getString("listallFooter");
        loginNewMails = config.getString("loginNewMails");
        emptyMail = config.getString("emptyMail");
        unknownTarget = config.getString("unknownTarget");
        messageSent = config.getString("messageSent");
        receivedNewMessage = config.getString("receivedNewMessage");
        commandError = config.getString("commandError");
        messageSentToAll = config.getString("messageSentToAll");
        help = config.getString("help");
        wrongSyntaxList = config.getString("wrongSyntax.list");
        wrongSyntaxListall = config.getString("wrongSyntax.listall");
        wrongSyntaxSend = config.getString("wrongSyntax.send");
        wrongSyntaxDelete = config.getString("wrongSyntax.del");
        noPermission = config.getString("noPermission");
        deletedAll = config.getString("deletedAll");
        deletedRead = config.getString("deletedRead");
        deletedSingle = config.getString("deletedSingle");

    }
}
