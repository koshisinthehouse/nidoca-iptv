package de.nidoca.webview.iptv.m3u;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import de.nidoca.webview.iptv.view.StationArrayAdapter;

public class LoadM3U extends AsyncTask<String, Void, List<Entry>> {

    private final WeakReference<Context> contextWR;
    private ListView listView;

    public LoadM3U(Context context, ListView listView) {
        this.contextWR = new WeakReference<>(context);
        this.listView = listView;
    }

    @Override
    protected List<Entry> doInBackground(String... strings) {
        List<Entry> entries;
        try {
            URL url = new URL(strings[0]);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            Parser parser = new Parser();
            InputStream inputStream = conn.getInputStream();
            entries = parser.parse(inputStream);
        } catch (Exception e) {
            Toast.makeText(contextWR.get(), "Fehler beim Laden der M3U URL", Toast.LENGTH_SHORT).show();
            throw new RuntimeException(e);
        }

        return entries;
    }

    @Override
    protected void onPostExecute(List<Entry> entries) {
        super.onPostExecute(entries);
        ArrayAdapter<Entry> adapter = new StationArrayAdapter(contextWR.get(),
                android.R.layout.simple_list_item_1,
                entries);
        listView.setAdapter(adapter);
    }

}
