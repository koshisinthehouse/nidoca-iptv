package de.nidoca.webview.iptv;

import android.os.Bundle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;

import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;

import androidx.appcompat.app.AppCompatActivity;

import androidx.navigation.ui.AppBarConfiguration;

import de.nidoca.webview.iptv.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;


public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        ExoPlayer player = new ExoPlayer.Builder(this).build();
        binding.playerView.setPlayer(player);



        CastContext castContext = CastContext.getSharedInstance(this);
        final CastPlayer castPlayer = new CastPlayer(castContext);

        castPlayer.setSessionAvailabilityListener(new SessionAvailabilityListener() {
            @Override
            public void onCastSessionAvailable() {
                System.out.println("ADD Cast MEdia");
                castPlayer.addMediaItem(player.getCurrentMediaItem());
            }

            @Override
            public void onCastSessionUnavailable() {
            }
        });
        ListView listView = binding.list;
        listView.setOnItemClickListener((parent, view, position, id) -> {
            player.stop();
            Entry entry = (Entry) parent.getItemAtPosition(position);
            System.out.println("Go: " + entry.getChannelUri());
            MediaItem mediaItem = MediaItem.fromUri(entry.getChannelUri());
            player.clearMediaItems();
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        });

        new LoadM3U(this, listView).execute();


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}