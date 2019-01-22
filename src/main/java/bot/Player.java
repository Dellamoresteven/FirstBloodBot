package bot;

import com.opencsv.bean.CsvBindByName;
import net.dv8tion.jda.core.entities.Member;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@SuppressWarnings("NullableProblems")
public class Player implements Comparable<Player>
{
    @CsvBindByName(column = "id")
    private String id;

    @CsvBindByName(column = "ign")
    private String ign;

    @CsvBindByName(column = "elo")
    private int elo;

    @CsvBindByName(column = "wins")
    private int wins;

    @CsvBindByName(column = "losses")
    private int losses;

    @CsvBindByName(column = "date")
    private String lastPlayedStr;

    @CsvBindByName(column = "streak")
    private int streak;

    @CsvBindByName(column = "std")
    private int std;

    @CsvBindByName(column = "stdp")
    private int stdp;

    @CsvBindByName(column = "prev")
    private boolean prev;

    @CsvBindByName(column = "accept")
    private boolean accepted;

    private Member member;
    private LocalDate lastPlayed;
    private LocalDate decayDate;

    public Player()
    {

    }

//    Player(String _id, String _ign, int _elo, int _wins, int _losses, int _streak)
//    {
//        id = _id;
//        ign = _ign;
//        elo = _elo;
//        wins = _wins;
//        losses = _losses;
//        streak = _streak;
//        std = 20;
//        stdp = 10;
//        prev = false;
//        accepted = false;
//    }

    Player(String _id, String _ign, int _elo, int _wins, int _losses, int _streak, int _std, int _stdp, boolean _prev, boolean _accepted)
    {
        lastPlayedStr = "12-31-2037";
        id = _id;
        ign = _ign;
        elo = _elo;
        wins = _wins;
        losses = _losses;
        streak = _streak;
        std = _std;
        stdp = _stdp;
        prev = _prev;
        accepted = _accepted;
    }

    void win()
    {
        ++wins;

        if (prev)
            ++streak;
        else
            streak = 1;

        prev = true;
    }

    void loss()
    {
        ++losses;

        if (!prev)
            --streak;
        else
            streak = -1;

        prev = false;
    }

    void updateIgn(String newIgn)
    {
        ign = newIgn;
    }

    void giveElo(int lp)
    {
        elo += lp;
    }

    void takeElo(int lp)
    {
        elo -= lp;
    }

    int updateElo(int enemyAvg, double score)
    {
        int old = elo;
        int change;
        double expected = 1 / (1 + Math.pow(10.0, (double)(enemyAvg - old) / 400));

        if (score == 0.0)
            change = (int)(expected - ((std /*- stdp*/ - (streak / 2)) * (2 - (1.0 - expected))));
        else
            change = (int)(expected + ((std /*+ stdp*/ + (streak / 2)) * (2 - expected)));
        /*
        if (stdp > 0)
            --stdp;
        */
        if (std > 13)
            --std;
        if (score == 0.0 && change > 0)
        {
            change *= 0.8;
            elo += -change;
            return -change;
        }
        change *= 1.1;
        elo += change;

        return change;
    }

    public int getWins()
    {
        return wins;
    }

    public int getLosses()
    {
        return losses;
    }

    public int getElo()
    {
        return elo;
    }

    public String getId()
    {
        return id;
    }

    public String getIgn()
    {
        return ign;
    }

    public int getStd()
    {
        return std;
    }

    public int getStdp()
    {
        return stdp;
    }

    public boolean getPrev()
    {
        return prev;
    }

    public boolean getAccepted()
    {
        return accepted;
    }

    public void accept()
    {
        accepted = true;
    }

    public String getLastPlayedStr()
    {
        return lastPlayedStr;
    }

    public LocalDate getLastPlayed()
    {
        return lastPlayed;
    }

    public int getStreak()
    {
        return streak;
    }

    Member getMember()
    {
        return member;
    }

    void setLastPlayedStr(String newStr)
    {
        lastPlayedStr = newStr;
    }

    void setMember(Member mem)
    {
        member = mem;
    }

    void decay(int amount)
    {
        elo -= amount;
    }

    void convertDate()
    {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-d-yyyy");
            formatter = formatter.withLocale(Locale.ENGLISH);
            lastPlayed = LocalDate.parse(lastPlayedStr, formatter);
            decayDate = lastPlayed.plusDays(4);
    }

    void updateDate()
    {
        lastPlayed = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-d-yyyy");
        lastPlayedStr = lastPlayed.format(formatter);
        decayDate = lastPlayed.plusDays(4);

    }

    LocalDate getDecayDate()
    {
        return decayDate;
    }

    @Override
    public int compareTo(Player player)
    {
        int compareQnt = player.elo;
        return compareQnt - elo;
    }

    @Override
    public String toString()
    {
        return ign;
    }
}
