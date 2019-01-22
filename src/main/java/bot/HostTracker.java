package bot;

import com.opencsv.CSVWriter;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import net.dv8tion.jda.core.entities.Member;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

class HostTracker
{
    private static final String fileName = "hosts.csv";
    private List<Host> ladder;

    HostTracker()
    {
        try
        {
            Reader reader = Files.newBufferedReader(Paths.get(fileName));
            CsvToBean<Host> parser = new CsvToBeanBuilder<Host>(reader)
                    .withType(Host.class)
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

    List<Host> getHosts()
    {
        return ladder;
    }

    void Update(Member host)
    {
        String id = host.getUser().getId();
        for (Host h : ladder)
        {
            if (h.getId().equals(id))
            {
                h.increment();
                return;
            }
        }
        ladder.add(new Host(id));
        write();
    }

    private void write()
    {
        try
        {
            Writer writer = Files.newBufferedWriter(Paths.get(fileName));
            StatefulBeanToCsv<Host> beanToCsv = new StatefulBeanToCsvBuilder<Host>(writer)
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
}
