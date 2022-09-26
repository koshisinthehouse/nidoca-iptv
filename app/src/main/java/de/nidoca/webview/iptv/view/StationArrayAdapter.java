package de.nidoca.webview.iptv.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.List;

import de.nidoca.webview.iptv.R;
import de.nidoca.webview.iptv.m3u.Entry;

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
        TextView tvHome = (TextView) convertView.findViewById(R.id.tv_home);
        // Populate the data into the template view using the data object
        tvName.setText(entry.getTvgId());
        tvHome.setText(entry.getChannelName());
        // Return the completed view to render on screen

        String tvgLogoUrl = entry.getTvgLogo();
        ImageView imageView = (ImageView) convertView.findViewById(R.id.station_view);
        new LoadImage(imageView).execute(tvgLogoUrl);

        return convertView;
    }

    public static class LoadImage extends AsyncTask<String, Void, Bitmap> {

        private final WeakReference<ImageView> imageViewWeakReference;

        public LoadImage(ImageView imageView) {
            this.imageViewWeakReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... strings) {
            Bitmap bmp = null;
            String tvgLogo = strings[0];
            if (tvgLogo != null && Patterns.WEB_URL.matcher(tvgLogo).matches()) {
                try {
                    URL url = new URL(tvgLogo);
                    bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                } catch (IOException e) {
                    ImageView imageView = imageViewWeakReference.get();
                    if (imageView != null) {
                        Toast.makeText(imageView.getContext(), "", Toast.LENGTH_SHORT).show();
                    }
                    Log.e(this.getClass().getName(), "error loading station logo, url: ");
                }
            }
            return bmp;
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            super.onPostExecute(bmp);
            ImageView imageView = imageViewWeakReference.get();
            if (bmp != null && imageView != null) {
                imageView.setImageBitmap(bmp);
            }
        }
    }

}
