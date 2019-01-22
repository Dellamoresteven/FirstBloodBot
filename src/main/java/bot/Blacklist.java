package bot;

import java.io.*;
import java.util.ArrayList;

class Blacklist
{
    private ArrayList<String> blacklist;
    private final String fileName;

    Blacklist(String name)
    {
        fileName = name;

        try
        {
            File yourFile = new File(fileName);
            yourFile.createNewFile();

            BufferedReader br = new BufferedReader(new FileReader(fileName));


            blacklist = new ArrayList<>();

            for (String line; (line = br.readLine()) != null;)
            {
                blacklist.add(line);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    ArrayList<String> getList()
    {
        return blacklist;
    }

    boolean check(String tag)
    {
        for (String line : blacklist)
        {
            if (line.equals(tag))
                return true;
        }

        return false;
    }

    void add(String tag)
    {
        blacklist.add(tag);
        save();
    }

    boolean remove(String tag)
    {
        for (String line : blacklist)
        {
            if (line.equals(tag))
            {
                blacklist.remove(line);
                save();
                return true;
            }
        }

        return false;
    }

    private void save()
    {
        try
        {
            PrintWriter writer = new PrintWriter(fileName);
            writer.print("");
            writer.close();

            BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));

            for (String line : blacklist)
            {
                bw.write(line);
                bw.newLine();
            }

            bw.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
