package bot;

import net.dv8tion.jda.core.EmbedBuilder;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

class Decay extends Thread
{
    private final Hub hub;

    Decay(Hub f_hub)
    {
        hub = f_hub;
    }

    @Override
    public void run()
    {
        try
        {
            while (true)
            {
                checkDecay();
                Date tempDate = new Date();
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, 1);
                Inhouse.nextDecayCheck = cal.getTime();
                Thread.sleep(86400000);
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private void checkDecay()
    {
        List<Player> listCopy = new ArrayList<>(hub.getHubLadder().getLadder());
        listCopy.sort(Comparator.comparing(Player::getElo));
        Collections.reverse(listCopy);

        boolean decay = false;
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        Date date = new Date();
        StringBuilder decayList = new StringBuilder("```\n");
        EmbedBuilder decayEmbed = new EmbedBuilder();
        decayEmbed.setColor(new Color(0, 112, 52));
        decayEmbed.setFooter("Today at " + formatter.format(date), "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");
        decayEmbed.setAuthor("Daily Decay",
            "https://docs.google.com/spreadsheets/d/1BA-8ehtjIbXV1R3zsVGN1TyewesEgOhMr3M0RnL0YHc/edit?usp=sharing",
            "https://cdn.discordapp.com/attachments/401047926048030731/410285261336084480/Meeps_icon.png");

        for (int i = 0; i < 5; ++i)
        {
            Player p = listCopy.get(i);
            if(p.getElo() >= 1215) {
                if (ChronoUnit.DAYS.between(LocalDate.now(), p.getDecayDate()) <= 0) {
                    decay = true;
                    decayList.append(String.format("%-16s", p.getIgn()))
                            .append(p.getElo())
                            .append(" > ")
                            .append(p.getElo() - 15)
                            .append("   \n");
                    p.decay(15);
                }
            }
        }
        for (int i = 5; i < 10; ++i)
        {
            Player p = listCopy.get(i);
            if(p.getElo() >= 1206) {
                if (ChronoUnit.DAYS.between(LocalDate.now(), p.getDecayDate()) <= 0) {
                    decay = true;
                    decayList.append(String.format("%-16s", p.getIgn()))
                            .append(p.getElo())
                            .append(" > ")
                            .append(p.getElo() - 6)
                            .append("   \n");
                    p.decay(6);
                }
            }
        }
        for (int i = 10; i < 20; ++i)
        {
            Player p = listCopy.get(i);
            if(p.getElo() >= 1203) {
                if (ChronoUnit.DAYS.between(LocalDate.now(), p.getDecayDate()) <= 0) {
                    decay = true;
                    decayList.append(String.format("%-16s", p.getIgn()))
                            .append(p.getElo())
                            .append(" > ")
                            .append(p.getElo() - 3)
                            .append("   \n");
                    p.decay(3);
                }
            }
        }
        decayList.append("```");
        hub.getHubLadder().write();
        decayEmbed.setDescription((decay) ? decayList : "**No players up are up for decay.**");
        Inhouse.offChannel.sendMessage(decayEmbed.build()).complete();
    }
}
