package de.nidoca.webview.iptv.m3u;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import de.nidoca.webview.iptv.MainActivity;

public class LoadImage extends AsyncTask<String, Void, Bitmap> {

    private final ImageView imageView;

    public LoadImage(ImageView imageView) {
        this.imageView = imageView;
    }

    @Override
    protected Bitmap doInBackground(String... strings) {
        Bitmap bmp = null;
        String tvgLogo = strings[0];
        if (tvgLogo != null && tvgLogo.length() > 0) {
            try {
                URL url = new URL(tvgLogo);
                bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bmp;
    }

    @Override
    protected void onPostExecute(Bitmap bmp) {
        super.onPostExecute(bmp);
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        }

    }
}
