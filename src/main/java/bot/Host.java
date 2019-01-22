package bot;

import com.opencsv.bean.CsvBindByName;

public class Host
{
    @CsvBindByName(column = "id")
    private String id;

    /*@CsvBindByName(column = "discord")
    private String discord;*/

    @CsvBindByName(column = "hosts")
    private int hosts;

    public Host()
    {

    }

    Host(String _id/*, String _discord*/)
    {
        id = _id;
        //discord = _discord;
        hosts = 1;
    }

    public String getId()
    {
        return id;
    }

    /*public String getDiscord()
    {
        return discord;
    }*/

    public int getHosts()
    {
        return hosts;
    }

    public void increment()
    {
        ++hosts;
    }
}
