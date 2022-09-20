package de.nidoca.webview.iptv.m3u;

import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import de.nidoca.webview.iptv.MainActivity;

public class LoadM3U extends AsyncTask<String, Void, List<Entry>> {

    private final MainActivity mA;
    private ListView listView;

    public LoadM3U(MainActivity mainActivity, ListView listView) {
        this.mA = mainActivity;
        this.listView = listView;
    }

    @Override
    protected List<Entry> doInBackground(String... strings) {
        List<Entry> entries;

        try {
            // Create a URL for the desired page
            URL url = new URL("https://raw.githubusercontent.com/PrinzMichiDE/iptv-kodi-german/master/ip-tv-german.m3u"); //My text file location
            //First open the connection
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(60000); // timing out in a minute

            Parser parser = new Parser();
            InputStream inputStream = conn.getInputStream();
            entries = parser.parse(inputStream);

            entries.forEach(entry -> {

                //listItems.add(entry.getTvgName());
                System.out.println(entry);
            });

        } catch (Exception e) {
            throw new RuntimeException(e);

        }

        return entries;
    }

    @Override
    protected void onPostExecute(List<Entry> strings) {
        super.onPostExecute(strings);
        ArrayAdapter<Entry> adapter = new ArrayAdapter<Entry>(mA,
                android.R.layout.simple_list_item_1,
                strings);
        listView.setAdapter(adapter);



    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
        System.out.println("GO");
    }
}
