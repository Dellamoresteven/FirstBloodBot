package bot;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.GuildController;

import javax.security.auth.login.LoginException;
import java.util.Date;

class Inhouse extends ListenerAdapter
{
    public static TextChannel channel;
    public static TextChannel offChannel;
    public static TextChannel staffchannel;
    public static TextChannel testchannel;
    public static Date nextDecayCheck;
    private static Hub hub;
    private static final String staffId = "495453112677695488";
    private static final String testid = "457027096653594634";              //test channel but for names
//    private static final String hostId = "390700897375748096";              //test channel
//    private static final String gamingId = "457027096653594634";            //test channel
    private static final String hostId = "513407293573038130";           //Channel id for #Solo-League-Host
    private static final String gamingId = "305341751965777921";        //Channel id for #Solo-League-Gaming

    public static void main(String args[]) throws LoginException, InterruptedException
    {
        hub = new Hub();
        hub.initDates();

        JDA jda = new JDABuilder(AccountType.BOT)
                .setToken("NDQ4OTI2Mzg4MDc1MTAyMjA4.DedO1g.fIW9XzDZTCT6LCI2j3UsOSi5qn0")
                .buildBlocking();
        jda.addEventListener(new Inhouse());

        channel = jda.getTextChannelById(hostId);
        offChannel = jda.getTextChannelById(gamingId);
        staffchannel = jda.getTextChannelById(staffId);
        testchannel = jda.getTextChannelById(testid);

        Decay decayChecker = new Decay(hub);
        decayChecker.run();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // Command entered in #Solo-League-Host
        if (event.getMessage().getChannel().getId().equals(hostId))
        {
            if (event.getMessage().getContentRaw().equals("!debug"))
            {
                hub.debug();
            }
            else if (hub.pendingHostInput())
            {
                if (permissable(event.getMember()))
                {
                    hub.hostInput(event.getMessage());
                }
                else
                {
                    event.getMessage().delete().complete();
                }
            }
            else if (hub.pendingPickorderInput())
            {
                hub.pickorderInput(event.getMessage());
            }
            else if (hub.pendingDraftInput())
            {
                hub.draftInput(event.getMessage());
            }
            else
            {
                if (event.getMessage().getContentRaw().startsWith("!host"))
                {
                    if (permissable(event.getMember()))
                    {
                        startHost(event.getMessage());
                    }
                }
                else if (event.getMessage().getContentRaw().startsWith("!ign "))
                {
                        joinRoom(event.getMessage());
                }
                else if (event.getMessage().getContentRaw().equals("!cancel"))
                {
                    if (permissable(event.getMember()))
                    {
                        hub.cancel(event.getMember());
                    }
                }
                else if (event.getMessage().getContentRaw().equals("!undo"))
                {
                    if (permissable(event.getMember()))
                    {
                        hub.undo(event.getMessage());
                    }
                }
                /*
                else if (event.getMessage().getContentRaw().startsWith("!game "))
                {
                    if (permissable(event.getMember()))
                    {
                        hub.declareWinner(event.getMessage());
                    }
                }
                else if (event.getMessage().getContentRaw().startsWith("!kick "))
                {
                    if (permissable(event.getMember()))
                    {
                        hub.kick(event.getMessage());
                    }
                }
                else if (event.getMessage().getContentRaw().equals("!drop"))
                {
                    hub.drop(event.getMessage());
                }
                else if (!event.getAuthor().getId().equals("448926388075102208"))
                {
                    event.getMessage().delete().complete();
                }
                */
            }
        }
        // Command entered in #Solo-League-Gaming
        else if (event.getMessage().getChannel().getId().equals(gamingId))
        {
            if (event.getMessage().getContentRaw().equals("!games"))
            {
                hub.games(event.getMessage());
            }
            else if (event.getMessage().getContentRaw().startsWith("!test"))
            {
                if (permissable(event.getMember()))
                {
                    hub.test(event.getMessage());
                }
            }
            else if (event.getMessage().getContentRaw().startsWith("!name "))
            {
                if (permissable(event.getMember()))
                {
                    hub.changeName(event.getMessage().getContentRaw());
                }
            }
            /*
            else if (event.getMessage().getContentRaw().equals("!lobbies"))
            {
                hub.lobbies(event.getMessage());
            }
            else if (event.getMessage().getContentRaw().startsWith("!ladder"))
            {
                hub.ladder(event.getMessage());
            }
            else if (event.getMessage().getContentRaw().startsWith("!most"))
            {
                hub.most(event.getMessage());
            }
            else if (event.getMessage().getContentRaw().startsWith("!stats"))
            {
                hub.stats(event.getMessage());
            }
            else if (event.getMessage().getContentRaw().equals("!decay"))
            {
                hub.decayCountdown();
            }
            else if (event.getMessage().getContentRaw().equals("!tos"))
            {
                GuildController gc = new GuildController(event.getMessage().getGuild());
                Role role = gc.getGuild().getRolesByName("SLGPlayers", false).get(0);
                gc.addRolesToMember(event.getMember(), role).complete();
                hub.notify(event.getMember());
            }
            else if (event.getMessage().getContentRaw().startsWith("!whitelist "))
            {
                if (permissable(event.getMember()))
                {
                    hub.remove(event.getMember(), event.getMessage().getContentRaw());
                }
            }
            else if (event.getMessage().getContentRaw().startsWith("!blacklist "))
            {
                if (permissable(event.getMember()))
                {
                    hub.blacklist(event.getMember(), event.getMessage().getContentRaw());
                }
            }
            */
        }
        else if (event.getMessage().getChannel().getId().equals(staffId))
        {
            if (event.getMessage().getContentRaw().equals("!list"))
            {
                hub.list();
            }
            else if (event.getMessage().getContentRaw().equals("!hosts"))
            {
                hub.hosts();
            }
            else if (event.getMessage().getContentRaw().startsWith("!whitelist "))
            {
                if (permissable(event.getMember()))
                {
                    hub.remove(event.getMember(), event.getMessage().getContentRaw());
                }
            }
            else if (event.getMessage().getContentRaw().startsWith("!blacklist "))
            {
                if (permissable(event.getMember()))
                {
                    hub.blacklist(event.getMember(), event.getMessage().getContentRaw());
                }
            }
        }
    }

    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent event)
    {
        String msg = event.getMessage().getContentRaw();
        if (msg.equals("!accept"))
        {
            hub.accept(event.getAuthor().getId(), event.getChannel());
        }
    }

    //Used by the join command to make sure the user
    //entered an ign and pass it to the delegate.
    private void joinRoom(Message msg)
    {
        String msgArray[] = msg.getContentRaw().split(" ");
        StringBuilder ign = new StringBuilder();

        for (int i = 0; i < msgArray.length; ++i)
        {
            if (i != 0)
            {
                ign.append(msgArray[i]);
            }
        }

        if (!ign.toString().equals(""))
        {
            hub.join(msg.getMember(), ign.toString(), msg);
        }
    }

    //Used by the host command to determine if the host
    //entered an ign, and if so pass it to the appropriate
    //member function.
    private void startHost(Message message)
    {
        String msgArray[] = message.getContentRaw().split(" ");
        StringBuilder ign = new StringBuilder();

        for (int i = 0; i < msgArray.length; ++i)
        {
            if (i != 0)
            {
                ign.append(msgArray[i]);
            }
        }

        if (msgArray.length < 2)
        {
            System.out.println("IGN BLANK");
            hub.host(message.getMember(), message, true);
        }
        else
        {
            hub.host(message.getMember(), message, ign.toString());
        }
    }


    //Some commands call this function before delegating to the hub class
    //to confirmOrCancel that the user has the SLGMods role.
    private boolean permissable(Member member)
    {
        for (Role role : member.getRoles())
        {
            if (role.getName().equals("SLGMods"))
            {
                return true;
            }
        }

        return false;
    }
    private boolean permissable2(Member member)
    {
        for (Role role : member.getRoles())
        {
            if (role.getName().equals("SLGCouncil"))
            {
                return true;
            }
        }

        return false;
    }
}
