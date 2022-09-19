package de.nidoca.webview.iptv;


import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.ContextCompat;
import androidx.navigation.ui.AppBarConfiguration;

import de.nidoca.webview.iptv.cache.NidocaPlayerCache;
import de.nidoca.webview.iptv.databinding.ActivityMainBinding;
import de.nidoca.webview.iptv.m3u.Entry;
import de.nidoca.webview.iptv.m3u.LoadM3U;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity {


    private AppBarConfiguration appBarConfiguration;

    private ActivityMainBinding binding;

    // the local and remote players
    private ExoPlayer exoPlayer = null;
    private CastPlayer castPlayer = null;
    private Player currentPlayer = null;
    private StyledPlayerView playerView;
    private ViewGroup playerViewParent;


    // the Cast context
    private CastContext castContext;
    private MenuItem castButton;

    // Player state params


    @Override
    protected void onStart() {
        super.onStart();
        if (Util.SDK_INT >= 24) {
            initializePlayers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Util.SDK_INT < 24 || exoPlayer == null) {
            initializePlayers();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Util.SDK_INT < 24) {
            rememberState();
            releaseLocalPlayer();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Util.SDK_INT >= 24) {
            rememberState();
            releaseLocalPlayer();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        releaseRemotePlayer();
        currentPlayer = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playerView = binding.playerView;
        playerViewParent = (ViewGroup) playerView.getParent();
        playerView.setControllerAutoShow(false);

        playerView.setFullscreenButtonClickListener(isFullScreen -> {
            if (isFullScreen) {
                LinearLayout linearLayout = new LinearLayout(this);
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                ((ViewGroup) playerView.getParent()).removeView(playerView);
                linearLayout.addView(playerView);
                setContentView(linearLayout);
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
            } else {
                setContentView(binding.getRoot());
                ((ViewGroup) playerView.getParent()).removeView(playerView);
                playerViewParent.addView(playerView);


            }
        });


        setSupportActionBar(binding.toolbar);

        ListView listView = binding.list;
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = (Entry) parent.getItemAtPosition(position);
            com.google.android.exoplayer2.MediaMetadata mediaMetadata = new com.google.android.exoplayer2.MediaMetadata.Builder().setTitle(entry.getTvgName()).setSubtitle(entry.getTvgId()).setStation(entry.getChannelName()).build();
            NidocaPlayerCache.CURRENT_MEDIA_ITEM = new MediaItem.Builder().setUri(entry.getChannelUri()).setMediaMetadata(mediaMetadata).setTag(null).build();
            startPlayback();
        });

        new LoadM3U(this, listView).execute();


    }

    /**
     * Prepares the local and remote players for playback.
     */
    private void initializePlayers() {

        if (exoPlayer == null) {
            exoPlayer = new ExoPlayer.Builder(this).build();
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onEvents(Player player, Player.Events events) {
                    Player.Listener.super.onEvents(player, events);
                    System.out.println(player.isPlaying());
                    if (player.isPlaying() && !castPlayer.isCastSessionAvailable()) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            });
            playerView.setPlayer(exoPlayer);
        }

        if (castPlayer == null) {
            castContext = CastContext.getSharedInstance(this);
            castPlayer = new CastPlayer(castContext);
            castPlayer.setSessionAvailabilityListener(new SessionAvailabilityListener() {
                @Override
                public void onCastSessionAvailable() {
                    playOnPlayer(castPlayer);
                }

                @Override
                public void onCastSessionUnavailable() {
                    playOnPlayer(exoPlayer);
                }
            });
        }

        if (castPlayer != null && castPlayer.isCastSessionAvailable()) {
            playOnPlayer(castPlayer);
        } else {
            playOnPlayer(exoPlayer);
        }

    }

    /**
     * Sets the current player to the selected player and starts playback.
     */
    private void playOnPlayer(Player player) {
        if (currentPlayer == player) {
            return;
        }

        // save state from the existing player
        if (currentPlayer != null) {
            if (currentPlayer.getPlaybackState() != Player.STATE_ENDED) {
                this.rememberState();
            }
            currentPlayer.stop();
        }

        // set the new player
        currentPlayer = player;

        // set up the playback
        startPlayback();
    }

    /**
     * Sets the video on the current player (local or remote), whichever is active.
     */
    private void startPlayback() {

        if (NidocaPlayerCache.CURRENT_MEDIA_ITEM == null) {
            return;

        }

        if (exoPlayer != null && currentPlayer == exoPlayer) {
            exoPlayer.setMediaItem(NidocaPlayerCache.CURRENT_MEDIA_ITEM);
            exoPlayer.setPlayWhenReady(NidocaPlayerCache.PLAY_WHEN_READY);
            exoPlayer.seekTo(0, NidocaPlayerCache.PLAYBACK_POSITION);
            exoPlayer.prepare();
        }

        if (castPlayer != null && currentPlayer == castPlayer) {

            MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
            com.google.android.exoplayer2.MediaMetadata mediaMetadata = NidocaPlayerCache.CURRENT_MEDIA_ITEM.mediaMetadata;
            metadata.putString(MediaMetadata.KEY_TITLE, mediaMetadata.displayTitle != null ? mediaMetadata.displayTitle.toString() : "");
            metadata.putString(MediaMetadata.KEY_SUBTITLE, mediaMetadata.subtitle != null ? mediaMetadata.subtitle.toString() : "");

            //metadata.addImage(WebImage(Uri.parse("any-image-url")));
            JSONObject jsonObject = new JSONObject();
            try {
                JSONObject mediaItem = new JSONObject();
                mediaItem.put("uri", NidocaPlayerCache.CURRENT_MEDIA_ITEM.localConfiguration.uri.toString());
                mediaItem.put("mediaId", NidocaPlayerCache.CURRENT_MEDIA_ITEM.mediaId);
                jsonObject.put("mediaItem", mediaItem);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            MediaInfo mediaInfo = new MediaInfo.Builder(NidocaPlayerCache.CURRENT_MEDIA_ITEM.localConfiguration.uri.toString()).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setCustomData(jsonObject).setContentType(MimeTypes.APPLICATION_M3U8).setStreamDuration(exoPlayer.getDuration() * 1000).setMetadata(metadata).build();

            RemoteMediaClient remoteMediaClient = castContext.getSessionManager().getCurrentCastSession().getRemoteMediaClient();
            remoteMediaClient.load(new MediaLoadRequestData.Builder().setCustomData(jsonObject).setMediaInfo(mediaInfo).build());

        }
    }

    /**
     * Remembers the state of the playback of this Player.
     */
    private void rememberState() {
        NidocaPlayerCache.PLAY_WHEN_READY = this.currentPlayer.getPlayWhenReady();
        NidocaPlayerCache.PLAYBACK_POSITION = this.currentPlayer.getCurrentPosition();
    }

    /**
     * Releases the resources of the local player back to the system.
     */
    private void releaseLocalPlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
            binding.playerView.setPlayer(null);
        }
    }

    /**
     * Releases the resources of the remote player back to the system.
     */
    private void releaseRemotePlayer() {
        if (castPlayer != null) {
            castPlayer.setSessionAvailabilityListener(null);
            castPlayer.release();
            castPlayer = null;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        castButton = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
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