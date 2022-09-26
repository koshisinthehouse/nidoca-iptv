package de.nidoca.webview.iptv.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import de.nidoca.webview.iptv.R;
import de.nidoca.webview.iptv.m3u.Entry;
import de.nidoca.webview.iptv.m3u.LoadImage;

public class StationArrayAdapter extends ArrayAdapter<Entry> {

    public StationArrayAdapter(@NonNull Context context, int resource, @NonNull List<Entry> objects) {
        super(context, resource, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        Entry entry = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_view_item, parent, false);
        }
        // Lookup view for data population
        TextView tvName = (TextView) convertView.findViewById(R.id.tv_name);
        TextView tvHome = (TextView) convertView.findViewById(R.id.tvHome);
        // Populate the data into the template view using the data object
        tvName.setText(entry.getTvgId());
        tvHome.setText(entry.getChannelName());
        // Return the completed view to render on screen

        String tvgLogoUrl = entry.getTvgLogo();
        new LoadImage((ImageView) convertView.findViewById(R.id.imageView)).execute(tvgLogoUrl);

        return convertView;
    }

}
