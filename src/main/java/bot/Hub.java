package bot;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.managers.GuildController;
import net.rithms.riot.api.ApiConfig;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.constant.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

public class Hub
{
    private final Leaderboard ladder = new Leaderboard();
    private final HostTracker tracker = new HostTracker();
    private final Blacklist blist;

    private final Stack<List<Player>> backups;
    private final Cloudinary cloudinary;

    private final RiotApi api;

    private boolean pendingHostInput;
    private boolean pendingPickorderInput;
    private boolean pendingDraftInput;
    private boolean pendingConfirm;

    private Message msgToDelete;
    private Message hostMessage;
    private Message hostPing;
    private Message rosterMessage;
    private Message rosterPing;
    private Message pickorderMessage;
    private int activeRooms;
    private boolean currentActiveHost;

    private final Room rooms[];
    private Room currentRoom;

    enum Input
    {
        HOST,
        PICKORDER,
        DRAFT,
        NONE
    }

    Hub()
    {
        //Setup Blacklist
        blist = new Blacklist("blacklist.txt");

        //Setup League API.
        ApiConfig config = new ApiConfig().setKey("RGAPI-975def55-e480-4d11-a117-e381031f6a23");
        api = new RiotApi(config);

        //Create and configure cloudinary api object.
        Map<String, String> cloudConfig = new HashMap<>();
        cloudConfig.put("cloud_name", "");
        cloudConfig.put("api_key", "");
        cloudConfig.put("api_secret", "");
        cloudinary = new Cloudinary(cloudConfig);

        //Create and assign the three rooms.
        rooms = new Room[4];
        rooms[0] = new Room(1);
        rooms[1] = new Room(2);
        rooms[2] = new Room(3);
        rooms[3] = new Room(4);
        rooms[4] = new Room(5);

        //Stack for leaderboard backups.
        backups = new Stack<>();

        //State Variables.
        pendingHostInput = false;
        pendingPickorderInput = false;
        pendingDraftInput = false;
        pendingConfirm = false;

        //Objects to hold and declareWinner messages send by bot
        hostMessage = null;
        hostPing = null;
        rosterMessage = null;
        rosterPing = null;
        msgToDelete = null;
        pickorderMessage = null;

        currentRoom = rooms[0];
        currentActiveHost = false;
        activeRooms = 0;

        //Push the current ladder onto the backup stack
        backup();
    }

    void debug()
    {
        System.out.println("------DEBUG-------");
        System.out.println("Active Games: " + activeRooms);
        System.out.println("Active Host: " + currentActiveHost);
        System.out.println("------ROOMS-------");
        for (Room room : rooms)
        {
            System.out.println("Host: " + room.host);
            System.out.println("Spots Left: " + room.spotsLeft);
            System.out.println("Drafting: " + room.isDrafting());
            System.out.println("Running: " + room.isRunning());
            System.out.println("-----------------");
        }
    }

    void test(Message message)
    {
        //Used to test WIP features.
        System.out.println("Test");
    }

    void host(Member host, Message message, boolean popMessage)
    {
        if (!currentActiveHost && activeRooms < 5)
        {
            System.out.println("Host made by (" + message.getMember().getUser().getName() + ")");
            currentActiveHost = true;
            setCurrentRoom();
            currentRoom.host = host;
            currentRoom.setActive();

            if (popMessage)
            {
                ++activeRooms;
                mentionable(message);
                HostEmbed();
                mentionable(message);
                message.delete().complete();
            }
        }
    }

    void host(Member host, Message message, String ign)
    {
        String realIgn = validateIgn(ign);

        if (!currentActiveHost)
        {
            if (!realIgn.equals(""))
            {
                host(host, message, false);

                if (!ladder.exists(host.getUser().getId()))
                {
                    Player player = new Player(host.getUser().getId(), realIgn, 1200, 0, 0, 0, 20, 10, false, true);
                    currentRoom.addPlayer(player);
                    ladder.addPlayerToLadder(player);
                    System.out.println("Added (" + player.getIgn() + ") to the ladder");
                    notify(host);
                    player.setMember(host);
                }
                else
                {
                    currentRoom.addPlayer(ladder.getPlayer(host.getUser().getId()));
                    ladder.getPlayer(host.getUser().getId()).setMember(host);
                    ladder.getPlayer(host.getUser().getId()).updateIgn(realIgn);
                }
                ++activeRooms;
                HostEmbed();
            }

            message.delete().complete();
        }
    }

    void mentionable(Message message)
    {
        GuildController gc = new GuildController(message.getGuild());
        Role role = gc.getGuild().getRolesByName("SLGPlayers", false).get(0);
        role.isMentionable();
    }

    void join(Member member, String ign, Message message)
    {
        String realIgn = validateIgn(ign);

        if (blist.check(ign.toLowerCase().replaceAll("\\s+", ""))
                || blist.check(member.getUser().getId()))
        {
            Inhouse.offChannel.sendMessage(String.format("%s This account has been blacklisted " +
                    "due to a previous ban. If you have any evidence that this account should be " +
                    "removed from the blacklist, message a community leader.",
                    member.getAsMention())).complete();
            message.delete().complete();
            return;
        }
        if (!realIgn.equals("") && currentActiveHost &&
                !currentRoom.isFull() && !currentRoom.contains(member.getUser().getId()))
        {
            if (!ladder.exists(member.getUser().getId()))
            {
                Player player = new Player(member.getUser().getId(), realIgn, 1200, 0, 0, 0, 20, 10, false, true);
                ladder.addPlayerToLadder(player);
                ladder.write();
                notify(member);
                Player player1 = ladder.getPlayer(member.getUser().getId());
                if (player1.getIgn().equals(realIgn))
                {
                    if (!player1.getAccepted())
                    {
                        message.delete().complete();
                        return;
                    }
                    currentRoom.addPlayer(ladder.getPlayer(member.getUser().getId()));
                    System.out.println("Added (" + player1.getIgn() + ") to host");
                    ladder.getPlayer(member.getUser().getId()).setMember(member);
                }
                else
                {
                    Inhouse.offChannel.sendMessage(String.format("%s You're attempting to join an SLG game with " +
                                    "a different name than the one you're currently registered with (**%s**). " +
                                    "If you'd like to play with a different account, ask an SLGMod to change it," +
                                    " thank you."
                            , member.getAsMention(), player.getIgn())).complete();
                    message.delete().complete();
                    return;
                }
            }
            else
            {
                Player player = ladder.getPlayer(member.getUser().getId());
                if (player.getIgn().equals(realIgn))
                {
                    if (!player.getAccepted())
                    {
                        message.delete().complete();
                        return;
                    }
                    currentRoom.addPlayer(ladder.getPlayer(member.getUser().getId()));
                    System.out.println("Added (" + player.getIgn() + ") to host");
                    ladder.getPlayer(member.getUser().getId()).setMember(member);
                }
                else
                {
                    Inhouse.offChannel.sendMessage(String.format("%s You're attempting to join an SLG game with " +
                            "a different name than the one you're currently registered with (**%s**). " +
                            "If you'd like to play with a different account, ask an SLGMod to change it," +
                            " thank you."
                            , member.getAsMention(), player.getIgn())).complete();
                    message.delete().complete();
                    return;
                }
            }

            message.delete().complete();
            updateHostEmbed();

            if (currentRoom.isFull())
                full();
        }
        else
        {
            message.delete().complete();
        }
    }

    void accept(String id, PrivateChannel channel)
    {
        if (ladder.exists(id))
        {
            Player player = ladder.getPlayer(id);
            if (!player.getAccepted())
            {
                EmbedBuilder builder = new EmbedBuilder();
                builder.setTitle("Accepted!");
                builder.setDescription("Thank you for reading this, you no longer need to accept but I appreciate you reading this and accepting! UwU");
                builder.setColor(new Color(0, 255, 0));

                //player.accept();
                //ladder.write();

                channel.sendMessage(builder.build()).complete();
            }
        }
    }

    void notify(Member member)
    {
        String id = member.getUser().getId();
        if (ladder.exists(id))
        {
            sendToS(member);
        }
        else
        {
            Player player = new Player(id, "NULL", 1200, 0, 0, 0, 20, 10, false, true);
            ladder.addPlayerToLadder(player);
            ladder.write();
            sendToS(member);
        }
        Inhouse.offChannel.sendMessage(String.format("%s You have automatically accpeted the TOS(Term of service) by participating in SLG. Ask a @ SLGMod to set your IGN so that you may sign up!", member.getAsMention())).complete();
    }

    void sendToS(Member member)
    {
        member.getUser().openPrivateChannel().queue((dm) ->
        {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle("SLG Terms of Service");
            builder.setDescription("Hello! Thank you for joining SLG, we appreciate you wanting to participate in our inhouses! Since this is your first game we would just like to give you a link to our rule book here: https://goo.gl/5wBwqz\n" +
                    "\n" +
                    "During the regular season of SLG, there are prizes for ending in the top 5. With that in mind, we want the season to be both competitive and fun for everyone! So we will often host marathons and raffle off prizes for those participating.\n" +
                    "\n" +
                    "\n" +
                    "We reserve the right to not allow people to participate if they break any of our rules and/or cause a negative playing experience for others. We hope you understand the importance of following the rules and helping preserve the competitive integrity of our Solo League Games! \n" +
                    "\n" +
                    "**Requirements Needed To Play SLG**\n" +
                    "\n" +
                    "<:FB:343506406265323520> A **gold+ account with over 50 games** played on it. To give captains an accurate assessment of your champ pool and playing level. \n" +
                    "\n" +
                    "<:FB:343506406265323520> Using your main account! We have records of which accounts you've verified on the server with. If for whatever reason you cannot play on your main account, we will need proof or you will not be allowed to participate. ex. account banned, we will need a screenshot of the email with the name of the account. \n" +
                    "\n" +
                    "<:FB:343506406265323520> Ability to communicate! We strive to keep our in-houses competitive, so please be ready to be asked to talk to a mod, as well as other players! \n" +
                    "\n" +
                    "<:FB:343506406265323520> Typing or pinging are minimum requirements! If you want to not talk or do anything with a team, perhaps you should play a regular game mode of league of legends! \n" +
                    "\n" +
                    "\n" +
                    "Please respond with **!accept** or you will be unable to participate in any future games until done so.");
            builder.setColor(new Color(255, 255, 0));

            dm.sendMessage(builder.build()).complete();
        });
    }

    void changeName(String message)
    {
        String msgArray[] = message.split(" ");
        StringBuilder ign = new StringBuilder("");
        if (msgArray.length > 2)
        {
            String id = msgArray[1].replaceAll("[^0-9]", "");
            if (ladder.exists(id))
            {
                for (int i = 0; i < msgArray.length; ++i)
                {
                    if (i > 1)
                    {
                        ign.append(msgArray[i]);
                    }
                }

                String realIgn = validateIgn(ign.toString());
                if (!realIgn.equals(""))
                {
                    Player player = ladder.getPlayer(id);
                    String oldName = player.getIgn();
                    player.updateIgn(realIgn);
                    Inhouse.testchannel.sendMessage(String.format("<@%s> changed from **%s** to **%s**.", id, oldName, realIgn)).complete();
                    Inhouse.offChannel.sendMessage(String.format("<@%s> changed from **%s** to **%s**.", id, oldName, realIgn)).complete();
                    ladder.write();
                }
            }
        }
    }

    void blacklist(Member member, String message)
    {
        String array[] = message.split(" ");
        StringBuilder tag = new StringBuilder();
        String display = "";
        boolean ign = false;

        if (array.length > 1)
        {
            if (array[1].startsWith("<@"))
            {
                tag = new StringBuilder(array[1].replaceAll("[^0-9]", ""));
            }
            else
            {
                for (int i = 0; i < array.length; ++i)
                    if (i != 0)
                        tag.append(array[i]);

                display = validateIgn(tag.toString());
                if (display.equals(""))
                    return;
                else
                    ign = true;
            }

            if (!blist.check(tag.toString()))
            {
                blist.add(tag.toString().toLowerCase().replaceAll("\\s+", ""));

                if (ign)
                    Inhouse.staffchannel.sendMessage(String.format("Summoner **%s** has been blacklisted.",
                            display)).complete();
                else
                    Inhouse.staffchannel.sendMessage(String.format("Discord user <@%s> has been blacklisted.",
                            tag.toString())).complete();
            }
            else
            {
                Inhouse.staffchannel.sendMessage(String.format("%s This account is alread blacklisted.",
                        member.getAsMention())).complete();
            }
        }
    }

    void remove(Member member, String message)
    {
        String array[] = message.split(" ");
        String tag = "";
        String display = "";
        boolean ign = false;

        if (array.length > 1)
        {
            if (array[1].startsWith("<@"))
            {
                tag = array[1].replaceAll("[^0-9]", "");
            }
            else
            {
                for (int i = 0; i < array.length; ++i)
                    if (i != 0)
                        tag += array[i];

                display = validateIgn(tag);
                if (display.equals(""))
                    return;
                else
                    ign = true;
            }

            if (blist.remove(tag.toLowerCase().replaceAll("\\s+", "")))
            {
                if (ign)
                    Inhouse.staffchannel.sendMessage(String.format("Summoner **%s** has been whitelisted.",
                            display)).complete();
                else
                    Inhouse.staffchannel.sendMessage(String.format("Discord user <@%s> has been whitelisted.",
                            tag)).complete();
            }
            else
            {
                Inhouse.staffchannel.sendMessage(String.format("%s This account is not blacklisted.",
                        member.getAsMention())).complete();
            }
        }
    }

    void list()
    {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        EmbedBuilder listEmbed = new EmbedBuilder();
        ArrayList<String> list = blist.getList();

        listEmbed.setDescription("\n");
        listEmbed.setTitle("SLG Blacklist");
        listEmbed.setColor(Color.BLACK);
        listEmbed.setFooter(String.format("Today at %s", formatter.format(date)),
                "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");

        for (String tag : list)
        {
            char s[] = tag.toCharArray();
            if(Character.isDigit(s[1]))
                listEmbed.appendDescription("<@" + tag + ">\n");
            else
                listEmbed.appendDescription("IGN: " +tag + "\n");
        }

        Inhouse.staffchannel.sendMessage(listEmbed.build()).complete();
    }

    void lobbies(Message message)
    {
        EmbedBuilder lobbies = new EmbedBuilder();
        int lobby = 1;
        for (Room room: rooms)
        {
            StringBuilder status = new StringBuilder("*Inactive*\n");
            if (room.isActive())
            {
                status = new StringBuilder("*Active*\nStatus : ");
                if (room.isDrafting())
                    status.append("Drafting\n");
                else if (room.isRunning())
                    status.append("In Game\n");
                else
                    status.append("Open\n");
                status.append("Host : ").append(room.host.getAsMention());
            }
            lobbies.addField("__Game " + lobby++ + "__", status.toString(), true);
            lobbies.setColor(new Color(255, 0, 0));
            lobbies.setAuthor("SLG Games (Second Channel)",
                    "https://docs.google.com/spreadsheets/d/1BA-8ehtjIbXV1R3zsVGN1TyewesEgOhMr3M0RnL0YHc/edit?usp=sharing",
                    "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
        }
        Inhouse.offChannel.sendMessage(lobbies.build()).complete();
    }

    void games(Message message)
    {
        int count = 0;
        boolean playing = false;
        for (Room room : rooms)
        {
            ++count;
            if (room.isRunning())
            {
                int total = 0;
                for (Player player : ladder.getLadder())
                {
                    total += (player.getLosses() + player.getWins());
                }

                total /= 10;
                playing = true;
                EmbedBuilder game = new EmbedBuilder();
                game.setColor(new Color(0, 153, 255));
                game.setTitle("Game #" + count + " (" + (total + count) + ")(Second Channel)");
                game.addField("__Blue Team__",
                        "```\n1. " + room.teamOne.get(0) + "\n" +
                        "2. " + room.teamOne.get(1) + "\n" +
                        "3. " + room.teamOne.get(2) + "\n" +
                        "4. " + room.teamOne.get(3) + "\n" +
                        "5. " + room.teamOne.get(4) + "\n```"
                        , false);
                game.addField("__Red Team__",
                        "```\n1. " + room.teamTwo.get(0) + "\n" +
                                "2. " + room.teamTwo.get(1) + "\n" +
                                "3. " + room.teamTwo.get(2) + "\n" +
                                "4. " + room.teamTwo.get(3) + "\n" +
                                "5. " + room.teamTwo.get(4) + "\n```"
                        , false);
                game.setFooter("Host: " + room.host.getEffectiveName(), room.host.getUser().getEffectiveAvatarUrl());
                game.setDescription("There are a total of **" + total + "** games played this season.");
                message.getChannel().sendMessage(game.build()).complete();
            }
        }

        if (!playing)
        {
            int total = 0;
            for (Player player : ladder.getLadder())
            {
                total += (player.getLosses() + player.getWins());
            }

            total /= 10;

            EmbedBuilder games = new EmbedBuilder();
            games.setColor(new Color(0, 153, 255));
            games.setTitle("No Games Being Played! (Second Channel)");
            games.setDescription("Although there aren't any games going on right now, there have been **" + total + "** played this season.");
            message.getChannel().sendMessage(games.build()).complete();
        }
    }

    void ladder(Message message)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        Date date = new Date();
        int page = 0;
        String contents[] = message.getContentRaw().split(" ");

        if (contents.length > 1)
            page = Integer.parseInt(contents[1]) - 1;

        int startRank = page * 20;
        int endRank = startRank + 20;

        List<Player> listCopy = new ArrayList<>(ladder.getLadder());
        listCopy.sort(Comparator.comparing(Player::getElo));
        Collections.reverse(listCopy);

        StringBuilder desc = new StringBuilder("```css\nRank " + String.format("%-16s", "IGN") + " Elo-W-L" + "     \n```");
        StringBuilder descTwo = new StringBuilder("```fix\n");
        StringBuilder descThree = new StringBuilder("```\n");
        for (int i = startRank; i < endRank; ++i)
        {
            if (i < listCopy.size())
            {
                Player local = listCopy.get(i);

                if (local != null)
                {
                    if (i > 4)
                    {
                            descThree.append(i + 1)
                                    .append(i > 8 ? ". " : ".  ")
                                    .append(String.format("%-16s", local.getIgn()))
                                    .append(String.format("%-4s ", local.getElo()))
                                    .append(String.format("%-2s ", local.getWins()))
                                    .append(local.getLosses())
                                    .append("    \n");
                    }
                    else
                    {
                            descTwo.append(i + 1)
                                    .append(". ")
                                    .append(String.format("%-16s", local.getIgn()))
                                    .append(String.format("%-4s ", local.getElo()))
                                    .append(String.format("%-2s ", local.getWins()))
                                    .append(local.getLosses())
                                    .append("    \n");
                    }
                }
            }
        }
        descTwo.append("```");
        descThree.append("```");

        if (page == 0)
            desc.append(descTwo);
        desc.append(descThree);

        EmbedBuilder lbBuilder = new EmbedBuilder();
        lbBuilder.setColor(new Color(0, 153, 255));
        lbBuilder.setDescription(desc);
        lbBuilder.setTitle("    :medal: __ SLG Leaderboards - Page " + (page == 0 ? 1 : page + 1) + "__ :medal:     ");
        lbBuilder.setFooter("Today at " + formatter.format(date), "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
        Inhouse.offChannel.sendMessage(lbBuilder.build()).complete();
    }

    void most(Message message)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        Date date = new Date();
        int page = 0;
        String contents[] = message.getContentRaw().split(" ");
        if (contents.length > 1)
            page = Integer.parseInt(contents[1]) - 1;

        int startRank = page * 10;
        int endRank = startRank + 10;

        List<Player> listCopy = new ArrayList<>(ladder.getLadder());
        listCopy.sort(Comparator.comparing(x -> (x.getWins() + x.getLosses())));
        Collections.reverse(listCopy);
        StringBuilder desc = new StringBuilder("```css\nRank-" + String.format("%-16s", "IGN") + "  Elo   Games" + "\n```");
        StringBuilder descThree = new StringBuilder("```\n");

        for (int i = startRank; i < endRank; ++i)
        {
            if (i < listCopy.size())
            {
                Player local = listCopy.get(i);

                if (local != null)
                {
                    descThree.append(i + 1)
                            .append(i > 8 ? ". " : ".  ")
                            .append(String.format(" %-16s", local.getIgn()))
                            .append("  ")
                            .append(String.format("%-4s", local.getElo()))
                            .append("  ")
                            .append(String.format("%-2s", local.getWins() + local.getLosses()))
                            .append("\n");
                }
            }
        }

        descThree.append("```");
        desc.append(descThree);

        EmbedBuilder lbBuilder = new EmbedBuilder();
        lbBuilder.setColor(new Color(0, 153, 255));
        lbBuilder.setDescription(desc);
        lbBuilder.setTitle(":medal: __ SLG Most Games Played - Page " + (page == 0 ? 1 : page + 1) + "__ :medal:");
        lbBuilder.setFooter("Today at " + formatter.format(date), "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
        Inhouse.offChannel.sendMessage(lbBuilder.build()).complete();
    }

    void hosts()
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        Date date = new Date();
        List<Host> hosts = new ArrayList<>(tracker.getHosts());
        hosts.sort(Comparator.comparing(Host::getHosts));
        Collections.reverse(hosts);
        EmbedBuilder hostsEmbed = new EmbedBuilder();
        hostsEmbed.setColor(new Color(0, 153, 255));
        hostsEmbed.setTitle("SLGMods # Games Hosted");
        StringBuilder buffer = new StringBuilder("\n");

        for (int i = 0; i < hosts.size(); ++i)
        {
            String idcheck = hosts.get(i).getId();
            if(idcheck.length() == 17) buffer.append(String.format("[%d] <@%-17s> %d\n", i + 1, hosts.get(i).getId(), hosts.get(i).getHosts()));
            else buffer.append(String.format("[%d] <@%-18s> %d\n", i + 1, hosts.get(i).getId(), hosts.get(i).getHosts()));
        }

        hostsEmbed.setFooter("Today at " + formatter.format(date),
                "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
        hostsEmbed.setDescription(buffer.toString());
        Inhouse.staffchannel.sendMessage(hostsEmbed.build()).complete();
    }

    void stats(Message message)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        Date date = new Date();
        String contents[] = message.getContentRaw().split(" ");
        String id = message.getAuthor().getId();
        if (contents.length > 1)
        {
            id = contents[1].replaceAll("[^\\d]", "" );
        }

        if (ladder.exists(message.getAuthor().getId()))
        {
            List<Player> listCopy = new ArrayList<>(ladder.getLadder());
            listCopy.sort(Comparator.comparing(Player::getElo));
            Collections.reverse(listCopy);

            for (int i = 0; i < listCopy.size(); ++i)
            {
                if (listCopy.get(i).getId().equals(id))
                {
                    Player local = listCopy.get(i);

                    if (local != null)
                    {
                        EmbedBuilder lbBuilder = new EmbedBuilder();
                        StringBuilder desc = new StringBuilder("```css\nRank " + String.format("%-16s", "IGN") + " Elo-W-L" + "     \n```");
                        StringBuilder descTwo = new StringBuilder("```\n");
                        descTwo.append(i + 1)
                                .append(i > 8 ? ". " : ".  ")
                                .append(String.format("%-16s", local.getIgn()))
                                .append(String.format("%-4s-", local.getElo()))
                                .append(String.format("%-2s-", local.getWins()))
                                .append(local.getLosses())
                                .append("    \n")
                                .append("```");
                        desc.append(descTwo);

                        if (i < 4)
                            desc.append("_*Will lose_ **")
                                    .append((i < 19) ? 15 : 15)
                                    .append("** _elo on_ **")
                                    .append(local.getDecayDate())
                                    .append("** _and each following\nday that they remain inactive._");

                        else if (i < 9)
                            desc.append("_*Will lose_ **")
                                    .append((i < 9) ? 6 : 6)
                                    .append("** _elo on_ **")
                                    .append(local.getDecayDate())
                                    .append("** _and each following\nday that they remain inactive._");

                        else if (i < 19)
                            desc.append("_*Will lose_ **")
                                    .append((i < 5) ? 3 : 3)
                                    .append("** _elo on_ **")
                                    .append(local.getDecayDate())
                                    .append("** _and each following\nday that they remain inactive._");

                        lbBuilder.setColor(new Color(0, 153, 255));
                        lbBuilder.setTitle("Stats for " + local.getIgn());
                        lbBuilder.setFooter("Today at " + formatter.format(date), "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
                        lbBuilder.setDescription(desc);
                        Inhouse.offChannel.sendMessage(lbBuilder.build()).complete();
                    }
                }
            }
        }
    }

    void cancel(Member member)
    {
        if (currentActiveHost)
        {
            EmbedBuilder cancelEmbed = new EmbedBuilder();
            cancelEmbed.setAuthor("Game " + (currentRoom.getNum()),
                    "https://docs.google.com/spreadsheets/d/1BA-8ehtjIbXV1R3zsVGN1TyewesEgOhMr3M0RnL0YHc/edit?usp=sharing",
                    "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
            cancelEmbed.setFooter("Host: " + currentRoom.host.getEffectiveName(), currentRoom.host.getUser().getEffectiveAvatarUrl());
            cancelEmbed.setDescription("<:FB:343506406265323520> **Canceled.**");
            cancelEmbed.setColor(new Color(255, 0, 0));

            currentActiveHost = false;
            currentRoom.clear();
            switchInput(Input.NONE);
            --activeRooms;
            clearMessages();

            Inhouse.channel.sendMessage(cancelEmbed.build()).complete();
        }
    }

    void kick(Message message)
    {
        String msgArray[] = message.getContentRaw().split(" ");
        if (msgArray.length == 2)
        {
            if (currentRoom.removePlayer(msgArray[1]))
            {
                //System.out.println("Kicked (" + message.getMember().getAsMention().equals(msgArray[1]) + ")");
                updateHostEmbed();
            }
        }

        message.delete().complete();
    }

    void drop(Message message)
    {
        if (currentRoom.removePlayer(message.getMember().getAsMention()))
        {
            System.out.println("(" + message.getMember().getEffectiveName() + ") dropped from host");
            updateHostEmbed();
        }

        message.delete().complete();
    }

    void undo(Message message)
    {
        ladder.setLadder(backups.pop());
        ladder.write();
        System.out.println("Game Undone");
        System.out.println("---------------------------------------------------");
        Inhouse.channel.sendMessage("**Ladder rolled back to previous rankings.**").complete();
    }

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

    void hostInput(Message message)
    {
        if (permissable(message.getMember()))//message.getMember().getUser().getId().equals(currentRoom.host.getUser().getId())
        {
            if (message.getContentRaw().equals("!cancel"))
            {
                System.out.println("Host canceled by (" + message.getMember().getEffectiveName() + ")");
                System.out.println("---------------------------------------------------");
                message.delete().complete();
                cancel(message.getMember());
            }
            else if (message.getContentRaw().startsWith("!kick "))
            {
                String msgArray[] = message.getContentRaw().split(" ");
                if (msgArray.length == 2)
                {
                    if (currentRoom.removePlayer(msgArray[1]))
                    {
                        switchInput(Input.NONE);

                        if (rosterMessage != null)
                        {
                            rosterMessage.delete().complete();
                            rosterPing.delete().complete();
                            rosterMessage = null;

                            HostEmbed();
                        }
                    }
                }

                message.delete().complete();
            }
            else if (message.getMember().getUser().getId().equals(currentRoom.host.getUser().getId())&&message.getContentRaw().equals("!draft"))
            {
                System.out.println("("+ currentRoom.host.getUser().getName() + ") started draft");
                switchInput(Input.PICKORDER);
                message.delete().complete();
                rosterPing.delete().complete();
                rosterPing = null;
                updateRosterEmbed();
                pickorderMessage = Inhouse.channel.sendMessage("Drafting has begun! Captain Two (" +
                        currentRoom.captainTwo.getMember().getAsMention() +
                        ")," +
                        " decide if you'd rather have" +
                        " **!first** or **!second** pick.")
                        .complete();
            }
        }
        else
        {
            if (!message.getMember().getUser().getId().equals("448926388075102208"))
            {
                message.delete().complete();
            }
        }
    }

    void pickorderInput(Message message)
    {
        if (message.getMember().getUser().getId().equals(currentRoom.captainTwo.getId()))
        {
            if (message.getContentRaw().equals("!first") || message.getContentRaw().equals("!second"))
            {
                if (message.getContentRaw().equals("!first"))
                {
                    System.out.println("First pick (" + currentRoom.captainTwo.getIgn() + ")");
                    System.out.println("Second pick (" + currentRoom.captainOne.getIgn() + ")");
                    currentRoom.firstPick = currentRoom.captainTwo;
                    currentRoom.secondPick = currentRoom.captainOne;
                    currentRoom.addPlayerToTeam(currentRoom.captainTwo, Room.Team.ONE);
                    currentRoom.addPlayerToTeam(currentRoom.captainOne, Room.Team.TWO);
                }
                else if (message.getContentRaw().equals("!second"))
                {
                    System.out.println("First pick (" + currentRoom.captainOne.getIgn() + ")");
                    System.out.println("Second pick (" + currentRoom.captainTwo.getIgn() + ")");
                    currentRoom.firstPick = currentRoom.captainOne;
                    currentRoom.secondPick = currentRoom.captainTwo;
                    currentRoom.addPlayerToTeam(currentRoom.captainOne, Room.Team.ONE);
                    currentRoom.addPlayerToTeam(currentRoom.captainTwo, Room.Team.TWO);
                }

                pickorderMessage.delete().complete();
                message.delete().complete();
                currentRoom.setCurrentPick(currentRoom.firstPick);
                switchInput(Input.DRAFT);
                currentRoom.setDrafting();
                msgToDelete = Inhouse.channel.sendMessage(currentRoom.firstPick.getMember().getAsMention() +
                        " Pick your first player.").complete();

                updateRosterTeamsEmbed();
            }
        }
        else if (permissable(message.getMember()))
        {
            if (message.getContentRaw().equals("!cancel"))
            {
                System.out.println("Host canceled by (" + message.getMember().getEffectiveName() + ")");
                System.out.println("---------------------------------------------------");
                message.delete().complete();
                cancel(message.getMember());
            }
        }
        else
        {
            if (!message.getMember().getUser().getId().equals("448926388075102208"))
                message.delete().complete();
        }
    }

    void draftInput(Message message)
    {
        if (message.getMember().getUser().getId().equals(currentRoom.getCurrentPick().getId()))
        {
            try
            {
                int pick = Integer.parseInt(message.getContentRaw());
                System.out.print(currentRoom.getCurrentPick().getIgn() + " picked ");
                if (currentRoom.addPlayerToTeam(pick) && currentRoom.isDrafting())
                {
                    msgToDelete.delete().complete();
                    message.delete().complete();
                    updateRosterTeamsEmbed();
                    msgToDelete = Inhouse.channel.sendMessage(currentRoom.getCurrentPick().getMember().getAsMention() +
                            " Pick a player.").complete();
                }
                else if (!currentRoom.isDrafting())
                {
                    System.out.println("DRAFTING FINISHED");
                    message.delete().complete();
                    clearMessages();
                    Inhouse.channel.sendMessage(currentRoom.teamEmbed(Room.Team.ONE)).complete();
                    Inhouse.channel.sendMessage(currentRoom.teamEmbed(Room.Team.TWO)).complete();
                    Inhouse.channel.sendMessage("Host is " + currentRoom.host.getEffectiveName()).complete();

                    for (Player player : currentRoom.players)
                    {
                        player.updateDate();
                    }
                    currentRoom.setRunning();
                    currentActiveHost = false;
                    switchInput(Input.NONE);
                }
            }
            catch (NumberFormatException e)
            {
                message.delete().complete();
            }
        }
        else if (permissable(message.getMember()))
        {
            if (message.getContentRaw().equals("!cancel"))
            {
                System.out.println("Host canceled by (" + message.getMember().getEffectiveName() + ")");
                System.out.println("---------------------------------------------------");
                message.delete().complete();
                cancel(message.getMember());
            }
        }
        else
        {
            if (!message.getMember().getUser().getId().equals("448926388075102208"))
            {
                message.delete().complete();
            }
        }
    }

    void declareWinner(Message message)
    {
        String msgArray[] = message.getContentRaw().split(" ");
        if (msgArray.length == 3 && (msgArray[2].equals("blue") || msgArray[2].equals("red")))
        {
            try
            {
                backup();
                Room game = rooms[(Integer.parseInt(msgArray[1]) - 1)];
                int averageBlue = game.averageElo(Room.Team.ONE);
                int averageRed = game.averageElo(Room.Team.TWO);

                List<Integer> oldWinnerElos = new ArrayList<>();
                List<Integer> oldLoserElos = new ArrayList<>();
                List<Player> winners = new ArrayList<>();
                List<Player> losers = new ArrayList<>();

                String winningTeam = msgArray[2].toLowerCase().substring(0, 1).toUpperCase() + msgArray[2].toLowerCase().substring(1);

                if (winningTeam.equals("Blue"))
                {
                    System.out.println("Win given to team Blue");
                    System.out.println("Loss givent to team Red");
                    winners = game.teamOne;
                    losers = game.teamTwo;
                    for (Player player : game.teamOne)
                    {
                        player.win();
                        oldWinnerElos.add(player.updateElo(averageRed, 1.0));
                    }
                    for (Player player : game.teamTwo)
                    {
                        player.loss();
                        oldLoserElos.add(player.updateElo(averageBlue, 0.0));
                    }
                }
                else if (winningTeam.equals("Red"))
                {
                    System.out.println("Win given to team Red");
                    System.out.println("Loss givent to team Blue");
                    winners = game.teamTwo;
                    losers = game.teamOne;
                    for (Player player : game.teamOne)
                    {
                        player.loss();
                        oldLoserElos.add(player.updateElo(averageRed, 0.0));
                    }
                    for (Player player : game.teamTwo)
                    {
                        player.win();
                        oldWinnerElos.add(player.updateElo(averageBlue, 1.0));
                    }
                }

                MessageEmbed winEmbed = createWinEmbed(winners, oldWinnerElos);
                MessageEmbed lossEmbed = createLossEmbed(losers, oldLoserElos);
                Inhouse.channel.sendMessage(winEmbed).complete();
                Inhouse.channel.sendMessage(lossEmbed).complete();

                tracker.Update(game.getHost());
                ladder.write();
                game.clear();
                switchInput(Input.NONE);
                --activeRooms;
            }
            catch (NumberFormatException e)
            {
                e.printStackTrace();
            }
        }
        else if (msgArray.length == 3 && msgArray[2].equals("clear"))
        {
            Room game = rooms[(Integer.parseInt(msgArray[1]) - 1)];
            game.clear();
            switchInput(Input.NONE);
            Inhouse.channel.sendMessage("**Game Cleared. Thanks for playing!**").complete();
            System.out.println("Game Cleared by (" + message.getMember().getEffectiveName() + ")");
            System.out.println("---------------------------------------------------");
            --activeRooms;
        }
    }

    void initDates()
    {
        for (Player player : ladder.getLadder())
            player.convertDate();
    }

    void decayCountdown()
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        Date date = new Date();

        long hours = ChronoUnit.HOURS.between(date.toInstant(), Inhouse.nextDecayCheck.toInstant());
        long minutes = ChronoUnit.MINUTES.between(date.toInstant(), Inhouse.nextDecayCheck.toInstant());
        long seconds = ChronoUnit.SECONDS.between(date.toInstant(), Inhouse.nextDecayCheck.toInstant());

        EmbedBuilder counterEmbed = new EmbedBuilder();
        counterEmbed.setFooter("Today at " + formatter.format(date),
                "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
        counterEmbed.setColor(new Color(0, 112, 52));
        counterEmbed.setAuthor("Daily Decay",
                "https://docs.google.com/spreadsheets/d/1BA-8ehtjIbXV1R3zsVGN1TyewesEgOhMr3M0RnL0YHc/edit?usp=sharing",
                "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
        counterEmbed.setDescription("Next inactivity check is in **" + hours + "** Hours, **" +
                (minutes - (hours * 60)) + "** Minutes, **" + (seconds - (minutes * 60)) + "** Seconds.");

        Inhouse.offChannel.sendMessage(counterEmbed.build()).complete();
    }

    Leaderboard getHubLadder()
    {
        return ladder;
    }

    private void full()
    {
        currentRoom.assignCaptains();
        currentRoom.setTotalElo();

        RosterEmbed();
        switchInput(Input.HOST);
    }

    private void updateHostEmbed()
    {
        EmbedBuilder hostEmbed = new EmbedBuilder();
        hostEmbed.setTitle("Players in Lobby");
        hostEmbed.setColor(new Color(0, 153, 255));
        hostEmbed.setDescription("**New SLG game has begun!** Type `!ign username`\n" +
                "```" +
                "1.  " + ((currentRoom.players.size() > 0) ? currentRoom.players.get(0).getIgn() : " ") + "\n" +
                "2.  " + ((currentRoom.players.size() > 1) ? currentRoom.players.get(1).getIgn() : " ") + "\n" +
                "3.  " + ((currentRoom.players.size() > 2) ? currentRoom.players.get(2).getIgn() : " ") + "\n" +
                "4.  " + ((currentRoom.players.size() > 3) ? currentRoom.players.get(3).getIgn() : " ") + "\n" +
                "5.  " + ((currentRoom.players.size() > 4) ? currentRoom.players.get(4).getIgn() : " ") + "\n" +
                "6.  " + ((currentRoom.players.size() > 5) ? currentRoom.players.get(5).getIgn() : " ") + "\n" +
                "7.  " + ((currentRoom.players.size() > 6) ? currentRoom.players.get(6).getIgn() : " ") + "\n" +
                "8.  " + ((currentRoom.players.size() > 7) ? currentRoom.players.get(7).getIgn() : " ") + "\n" +
                "9.  " + ((currentRoom.players.size() > 8) ? currentRoom.players.get(8).getIgn() : " ") + "\n" +
                "10. " + ((currentRoom.players.size() > 9) ? currentRoom.players.get(9).getIgn() : " ") + "\n" +
                "```");
        hostEmbed.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                currentRoom.host.getUser().getEffectiveAvatarUrl());

        if (hostMessage != null)
        {
            hostMessage = hostMessage.editMessage(hostEmbed.build()).complete();
        }
    }

    private void updateRosterEmbed()
    {
        if (rosterMessage != null)
        {
            EmbedBuilder roster = new EmbedBuilder();
            roster.setTitle("SLG Inhouse Game");
            roster.setColor(new Color(0, 153, 255));
            roster.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                    currentRoom.host.getUser().getEffectiveAvatarUrl());

            StringBuilder desc = new StringBuilder("```\n");
            desc.append("C1")
                    .append(". ")
                    .append(currentRoom.captainOne.getIgn())
                    .append("\n");
            desc.append("C2")
                    .append(". ")
                    .append(currentRoom.captainTwo.getIgn())
                    .append("\n");
            for (int i : currentRoom.pickPool.keySet())
            {
                desc.append(i)
                        .append(".  ")
                        .append(currentRoom.pickPool.get(i))
                        .append("\n");
            }

            desc.append("```");
            roster.addField("Signed Up Players for Current Game", desc.toString(), false);

            rosterMessage = rosterMessage.editMessage(roster.build()).complete();
        }
    }

    private void updateRosterTeamsEmbed()
    {
        if (rosterMessage != null)
        {
            EmbedBuilder roster = new EmbedBuilder();
            roster.setTitle("SLG Draft Phase");
            roster.setColor(new Color(0, 153, 255));
            roster.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                    currentRoom.host.getUser().getEffectiveAvatarUrl());

            StringBuilder desc = new StringBuilder("```\n");
            for (int i : currentRoom.pickPool.keySet())
            {
                desc.append("[")
                        .append(i)
                        .append("] ")
                        .append(currentRoom.pickPool.get(i))
                        .append("\n");
            }
            desc.append("```");

            roster.addField("Remaining Pick Pool", desc.toString(), true);
            roster.addField("Game Info", "Host: **" +
                    currentRoom.host.getEffectiveName() +
                    "**\nBlue Captain: **" +
                    currentRoom.captainOne.getMember().getEffectiveName() +
                    "**\nRed Captain: **" +
                    currentRoom.captainTwo.getMember().getEffectiveName() +
                    "**\nTotal Combined Elo: **" +
                    currentRoom.getTotalElo() +
                    "**\nBlue Team Elo: **" +
                    currentRoom.getBlueElo() +
                    "**\nRed Team Elo: **" +
                    currentRoom.getRedElo() + "**", true);

            roster.addField("Blue Team", "```ini\n" +
                    "[1. " + String.format("%-22s", ((currentRoom.teamOne.size() > 0) ? currentRoom.teamOne.get(0).getIgn() : " ")) + "]" + "\n" +
                    "[2. " + String.format("%-22s", ((currentRoom.teamOne.size() > 1) ? currentRoom.teamOne.get(1).getIgn() : " ")) + "]" + "\n" +
                    "[3. " + String.format("%-22s", ((currentRoom.teamOne.size() > 2) ? currentRoom.teamOne.get(2).getIgn() : " ")) + "]" + "\n" +
                    "[4. " + String.format("%-22s", ((currentRoom.teamOne.size() > 3) ? currentRoom.teamOne.get(3).getIgn() : " ")) + "]" + "\n" +
                    "[5. " + String.format("%-22s", ((currentRoom.teamOne.size() > 4) ? currentRoom.teamOne.get(4).getIgn() : " ")) + "]" + "\n" +
                    "```", true);

            roster.addField("Red Team", "```css\n" +
                    "[1. " + String.format("%-22s", ((currentRoom.teamTwo.size() > 0) ? currentRoom.teamTwo.get(0).getIgn() : " ")) + "]" + "\n" +
                    "[2. " + String.format("%-22s", ((currentRoom.teamTwo.size() > 1) ? currentRoom.teamTwo.get(1).getIgn() : " ")) + "]" + "\n" +
                    "[3. " + String.format("%-22s", ((currentRoom.teamTwo.size() > 2) ? currentRoom.teamTwo.get(2).getIgn() : " ")) + "]" + "\n" +
                    "[4. " + String.format("%-22s", ((currentRoom.teamTwo.size() > 3) ? currentRoom.teamTwo.get(3).getIgn() : " ")) + "]" + "\n" +
                    "[5. " + String.format("%-22s", ((currentRoom.teamTwo.size() > 4) ? currentRoom.teamTwo.get(4).getIgn() : " ")) + "]" + "\n" +
                    "```", true);

            rosterMessage = rosterMessage.editMessage(roster.build()).complete();
        }
    }

    private void HostEmbed()
    {
        EmbedBuilder hostEmbed = new EmbedBuilder();
        hostEmbed.setTitle("Players in Lobby");
        hostEmbed.setColor(new Color(0, 153, 255));
        hostEmbed.setDescription("**New SLG game has begun!** Type `!ign username`\n" +
                "```" +
                "1.  " + ((currentRoom.players.size() > 0) ? currentRoom.players.get(0).getIgn() : " ") + "\n" +
                "2.  " + ((currentRoom.players.size() > 1) ? currentRoom.players.get(1).getIgn() : " ") + "\n" +
                "3.  " + ((currentRoom.players.size() > 2) ? currentRoom.players.get(2).getIgn() : " ") + "\n" +
                "4.  " + ((currentRoom.players.size() > 3) ? currentRoom.players.get(3).getIgn() : " ") + "\n" +
                "5.  " + ((currentRoom.players.size() > 4) ? currentRoom.players.get(4).getIgn() : " ") + "\n" +
                "6.  " + ((currentRoom.players.size() > 5) ? currentRoom.players.get(5).getIgn() : " ") + "\n" +
                "7.  " + ((currentRoom.players.size() > 6) ? currentRoom.players.get(6).getIgn() : " ") + "\n" +
                "8.  " + ((currentRoom.players.size() > 7) ? currentRoom.players.get(7).getIgn() : " ") + "\n" +
                "9.  " + ((currentRoom.players.size() > 8) ? currentRoom.players.get(8).getIgn() : " ") + "\n" +
                "10. " + ((currentRoom.players.size() > 9) ? currentRoom.players.get(9).getIgn() : " ") + "\n" +
                "```");
        hostEmbed.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                currentRoom.host.getUser().getEffectiveAvatarUrl());
        hostPing = Inhouse.channel.sendMessage("<@&319995997088645120>").complete();
        hostMessage = Inhouse.channel.sendMessage(hostEmbed.build()).complete();
    }

    private void RosterEmbed()
    {
        if (hostMessage != null)
        {
            hostMessage.delete().complete();
            hostPing.delete().complete();
            hostMessage = null;
            hostPing = null;
        }

        EmbedBuilder roster = new EmbedBuilder();
        roster.setTitle("SLG Inhouse Game");
        roster.addField("__Signed Up Players for Current Game__",
                "```1.  " + currentRoom.players.get(8).getIgn() + "\n" +
                        "2.  " + currentRoom.players.get(7).getIgn() + "\n" +
                        "3.  " + currentRoom.players.get(6).getIgn() + "\n" +
                        "4.  " + currentRoom.players.get(5).getIgn() + "\n" +
                        "5.  " + currentRoom.players.get(2).getIgn() + "\n" +
                        "6.  " + currentRoom.players.get(1).getIgn() + "\n" +
                        "7.  " + currentRoom.players.get(0).getIgn() + "\n" +
                        "8.  " + currentRoom.players.get(3).getIgn() + "\n" +
                        "9.  " + currentRoom.players.get(4).getIgn() + "\n" +
                        "10. " + currentRoom.players.get(9).getIgn() + "```"
                , false);
        roster.setColor(new Color(0, 153, 255));
        roster.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                currentRoom.host.getUser().getEffectiveAvatarUrl());
        rosterMessage = Inhouse.channel.sendMessage(roster.build()).complete();
        rosterPing = Inhouse.channel.sendMessage("**Lobby Full.** Wait for " +
                currentRoom.host.getAsMention() +
                " to begin the draft phase with **!draft**. `You will not be " +
                "allowed to drop from this point forward or face a warning/ban.`")
                .complete();
    }

    private void setCurrentRoom()
    {
        if (!rooms[0].isRunning())
        {
            currentRoom = rooms[0];
        }
        else if (!rooms[1].isRunning())
        {
            currentRoom = rooms[1];
        }
        else if (!rooms[2].isRunning())
        {
            currentRoom = rooms[2];
        }
        else if (!rooms[3].isRunning())
        {
            currentRoom = rooms[3];
        }
        else if (!rooms[4].isRunning())
        {
            currentRoom = rooms[4];
        }
    }

    private void clearMessages()
    {
        if (hostMessage != null)
        {
            hostMessage.delete().complete();
            hostMessage = null;
        }

        if (hostPing != null)
        {
            hostPing.delete().complete();
            hostPing = null;
        }

        if (rosterMessage != null)
        {
            rosterMessage.delete().complete();
            rosterMessage = null;
        }

        if (rosterPing != null)
        {
            rosterPing.delete().complete();
            rosterPing = null;
        }

        if (msgToDelete != null)
        {
            msgToDelete.delete().complete();
            msgToDelete = null;
        }
    }

    private void backup()
    {
        List<Player> backupLadder = new ArrayList<>();

        for (Player p : ladder.getLadder())
        {
            Player copy = new Player(p.getId(), p.getIgn(), p.getElo(), p.getWins(), p.getLosses(), p.getStreak(), p.getStd(), p.getStdp(), p.getPrev(), p.getAccepted());
            copy.setMember(p.getMember());
            copy.setLastPlayedStr(p.getLastPlayedStr());
            backupLadder.add(copy);
        }

        backups.push(backupLadder);
    }

    private String validateIgn(String ign)
    {
        try
        {
            Summoner summoner = api.getSummonerByName(Platform.NA, ign);
            return summoner.getName();
        }
        catch (RiotApiException | IllegalArgumentException e)
        {
            return "";
        }
    }

    private void switchInput(Input newInputType)
    {
        pendingHostInput = false;
        pendingPickorderInput = false;
        pendingDraftInput = false;
        pendingConfirm = false;

        switch (newInputType)
        {
            case HOST:
                pendingHostInput = true;
                break;

            case PICKORDER:
                pendingPickorderInput = true;
                break;

            case DRAFT:
                pendingDraftInput = true;
                break;

            case NONE:
                break;
        }
    }

    private MessageEmbed createWinEmbed(List<Player> winners, List<Integer> elos)
    {
        try
        {
            File imageFile = new File("postGameScreen.png");
            BufferedImage background = ImageIO.read(imageFile);
            Graphics g = background.getGraphics();
            g.setFont(g.getFont().deriveFont(68f));
            g.setColor(new Color(150, 140, 82));

            int pos = 135;
            for (Player player : winners)
            {
                g.drawString(player.getIgn(),  30, pos);
                pos += 200;
                System.out.print(player.getIgn() + ", ");
            }
            System.out.println("");
            System.out.println(elos.toString());
            g.setColor(new Color(76, 178, 73));

            pos = 135;
            for (Integer elo : elos)
            {
                g.drawString( "(+" + elo.toString() + ")", 755, pos);
                pos += 200;
            }

            ImageIO.write(background, "png", new File("temp.png"));
            g.dispose();

            final String uuid = UUID.randomUUID().toString();
            cloudinary.uploader().upload("temp.png", ObjectUtils.asMap("public_id", uuid));
            final String generate = cloudinary.url().generate(uuid + ".png");

            int total = 0;
            for (Player player : ladder.getLadder())
            {
                total += (player.getLosses() + player.getWins());
            }

            total /= 10;

            EmbedBuilder imageEmbed = new EmbedBuilder();
            imageEmbed.setImage(generate);
            imageEmbed.setColor(new Color(0, 127, 224));
            imageEmbed.setAuthor("Victory! Game #" + total + "",
                    "https://docs.google.com/spreadsheets/d/1BA-8ehtjIbXV1R3zsVGN1TyewesEgOhMr3M0RnL0YHc/edit?usp=sharing",
                    "https://cdn.discordapp.com/attachments/366468277800796160/407688596791754763/Victory.png");
            return imageEmbed.build();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private MessageEmbed createLossEmbed(List<Player> losers, List<Integer> elos)
    {
        try
        {
            File imageFile = new File("postGameScreen.png");
            BufferedImage background = ImageIO.read(imageFile);
            Graphics g = background.getGraphics();
            g.setFont(g.getFont().deriveFont(68f));
            g.setColor(new Color(150, 140, 82));

            int pos = 135;
            for (Player player : losers)
            {
                g.drawString(player.getIgn(),  30, pos);
                pos += 200;
                System.out.print(player.getIgn() + ", ");
            }
            System.out.println("");
            System.out.println(elos.toString());
            g.setColor(new Color(237, 73, 73));

            pos = 135;
            for (Integer elo : elos)
            {
                g.drawString( "(" + elo.toString() + ")", 755, pos);
                pos += 200;
            }

            ImageIO.write(background, "png", new File("temp.png"));
            g.dispose();

            final String uuid = UUID.randomUUID().toString();
            cloudinary.uploader().upload("temp.png", ObjectUtils.asMap("public_id", uuid));
            final String generate = cloudinary.url().generate(uuid + ".png");

            int total = 0;
            for (Player player : ladder.getLadder())
            {
                total += (player.getLosses() + player.getWins());
            }

            total /= 10;
            EmbedBuilder imageEmbed = new EmbedBuilder();
            imageEmbed.setImage(generate);
            imageEmbed.setColor(new Color(204, 67, 67));
            imageEmbed.setAuthor("Defeat! Game #" + total + "",
                    "https://docs.google.com/spreadsheets/d/1BA-8ehtjIbXV1R3zsVGN1TyewesEgOhMr3M0RnL0YHc/edit?usp=sharing",
                    "https://cdn.discordapp.com/attachments/366468277800796160/407688596791754763/Victory.png");
            System.out.println("---------------------------------------------------");
            return imageEmbed.build();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }

    }

    boolean pendingHostInput()
    {
        return pendingHostInput;
    }

    boolean pendingPickorderInput()
    {
        return pendingPickorderInput;
    }

    boolean pendingDraftInput()
    {
        return pendingDraftInput;
    }

    boolean pendingConfirm()
    {
        return pendingConfirm;
    }
}
