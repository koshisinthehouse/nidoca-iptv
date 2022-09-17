package de.nidoca.webview.iptv;

import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LoadM3U extends AsyncTask<String, Void, List<String>> {

    private final MainActivity mA;
    private ListView listView;

    public LoadM3U(MainActivity mainActivity, ListView listView) {
        this.mA = mainActivity;
        this.listView = listView;
    }

    @Override
    protected List<String> doInBackground(String... strings) {
        ArrayList<String> listItems = new ArrayList<String>();

        try {
            // Create a URL for the desired page
            URL url = new URL("https://raw.githubusercontent.com/PrinzMichiDE/iptv-kodi-german/master/ip-tv-german.m3u"); //My text file location
            //First open the connection
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(60000); // timing out in a minute


            Parser parser = new Parser();
            List<Entry> entries = parser.parse(conn.getInputStream());

            entries.forEach(entry -> {

                listItems.add(entry.getTvgName());
                System.out.println(entry);
            });

        } catch (Exception e) {
            throw new RuntimeException(e);

        }

        return listItems;
    }

    @Override
    protected void onPostExecute(List<String> strings) {
        super.onPostExecute(strings);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mA,
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
