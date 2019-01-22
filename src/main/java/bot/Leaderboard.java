package bot;

import com.opencsv.CSVWriter;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

class Leaderboard
{
    private static final String fileName = "ladder.csv";

    private List<Player> ladder;

    Leaderboard()
    {
        try
        {
            Reader reader = Files.newBufferedReader(Paths.get(fileName));
            CsvToBean<Player> parser = new CsvToBeanBuilder<Player>(reader)
                    .withType(Player.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            ladder = parser.parse();

            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    boolean exists(String id)
    {
        for (Player player : ladder)
            if (player.getId().equals(id))
                return true;

        return false;
    }

    Player getPlayer(String id)
    {
        for (Player player : ladder)
            if (player.getId().equals(id))
                return player;

        return null;
    }

    void addPlayerToLadder(Player player)
    {
        ladder.add(player);
    }

    void write()
    {
        try
        {
            Writer writer = Files.newBufferedWriter(Paths.get(fileName));
            StatefulBeanToCsv<Player> beanToCsv = new StatefulBeanToCsvBuilder<Player>(writer)
                    .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                    .build();
            beanToCsv.write(ladder);
            writer.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    List<Player> getLadder()
    {
        return ladder;
    }

    void setLadder(List<Player> newLadder)
    {
        ladder = newLadder;
    }
}
