package de.nidoca.webview.iptv;


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
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.navigation.ui.AppBarConfiguration;

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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements SessionAvailabilityListener {


    private AppBarConfiguration appBarConfiguration;

    private ActivityMainBinding binding;

    private MediaItem mediaItem;

    // the local and remote players
    private ExoPlayer exoPlayer = null;
    private CastPlayer castPlayer = null;
    private Player currentPlayer = null;
    private StyledPlayerView exoPlayerView;
    private ViewGroup playerViewParent;

    // the Cast context
    private CastContext castContext;
    private MenuItem castButton;

    @Override
    protected void onPause() {
        super.onPause();
        if (Util.SDK_INT < 24) {
            //rememberState();
            //releaseLocalPlayer();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Util.SDK_INT >= 24) {
            //rememberState();
            //releaseLocalPlayer();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        //releaseRemotePlayer();
        //currentPlayer = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //exoPlayerView - start
        exoPlayerView = binding.playerView;
        playerViewParent = (ViewGroup) exoPlayerView.getParent();
        exoPlayerView.setControllerAutoShow(false);
        exoPlayerView.setFullscreenButtonClickListener(isFullScreen -> {
            if (isFullScreen) {
                LinearLayout linearLayout = new LinearLayout(this);
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                ((ViewGroup) exoPlayerView.getParent()).removeView(exoPlayerView);
                linearLayout.addView(exoPlayerView);
                setContentView(linearLayout);
                exoPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
            } else {
                setContentView(binding.getRoot());
                ((ViewGroup) exoPlayerView.getParent()).removeView(exoPlayerView);
                playerViewParent.addView(exoPlayerView);
            }
        });
        //exoPlayerView - end

        //castPlayer - start
        CastContext
                .getSharedInstance(this, Executors.newSingleThreadExecutor()).addOnCompleteListener(new OnCompleteListener<CastContext>() {
                    @Override
                    public void onComplete(@NonNull Task<CastContext> task) {
                        castContext = task.getResult();
                        castPlayer = new CastPlayer(castContext);
                        castPlayer.setPlayWhenReady(true);
                        castPlayer.setSessionAvailabilityListener(MainActivity.this);
                    }
                });
        //castPlayer - end

        //exoPlayer - start
        //must init after CastContext
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player player, Player.Events events) {
                Player.Listener.super.onEvents(player, events);
                if (player.isPlaying() && !castPlayer.isCastSessionAvailable()) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        });
        exoPlayerView.setPlayer(exoPlayer);
        if (castPlayer != null && castPlayer.isCastSessionAvailable()) {
            switchCurrentPlayer(castPlayer);
        } else {
            switchCurrentPlayer(exoPlayer);
        }
        //exoPlayer - end

        //toolbar - start
        setSupportActionBar(binding.toolbar);
        //toolbar - end

        //listView - start
        ListView listView = binding.list;
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = (Entry) parent.getItemAtPosition(position);
            com.google.android.exoplayer2.MediaMetadata mediaMetadata = new com.google.android.exoplayer2.MediaMetadata.Builder().setTitle(entry.getTvgName()).setSubtitle(entry.getTvgId()).setStation(entry.getChannelName()).build();
            this.mediaItem = new MediaItem.Builder().setUri(entry.getChannelUri()).setMediaMetadata(mediaMetadata).setTag(null).build();
            startPlayback();
        });
        new LoadM3U(this, listView).execute();
        //listView - end


    }

    private void switchCurrentPlayer(Player newPlayer) {

        if (this.currentPlayer == newPlayer) {
            return;
        }

        if (this.currentPlayer != null) {
            currentPlayer.stop();
        }

        this.currentPlayer = newPlayer;

        if (currentPlayer == this.castPlayer) {
            exoPlayerView.setVisibility(View.INVISIBLE);
        } else {
            exoPlayerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets the video on the current player (local or remote), whichever is active.
     */
    private void startPlayback() {

        if (this.mediaItem == null) {
            return;

        }

        if (exoPlayer != null && currentPlayer == exoPlayer) {
            exoPlayer.setMediaItem(this.mediaItem);
            exoPlayer.prepare();
        }

        if (castPlayer != null && currentPlayer == castPlayer) {

            MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
            com.google.android.exoplayer2.MediaMetadata mediaMetadata = this.mediaItem.mediaMetadata;
            metadata.putString(MediaMetadata.KEY_TITLE, mediaMetadata.displayTitle != null ? mediaMetadata.displayTitle.toString() : "");
            metadata.putString(MediaMetadata.KEY_SUBTITLE, mediaMetadata.subtitle != null ? mediaMetadata.subtitle.toString() : "");

            //metadata.addImage(WebImage(Uri.parse("any-image-url")));
            JSONObject jsonObject = new JSONObject();
            try {
                JSONObject mediaItem = new JSONObject();
                mediaItem.put("uri", this.mediaItem.localConfiguration.uri.toString());
                mediaItem.put("mediaId", this.mediaItem.mediaId);
                jsonObject.put("mediaItem", mediaItem);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            MediaInfo mediaInfo = new MediaInfo.Builder(this.mediaItem.localConfiguration.uri.toString()).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setCustomData(jsonObject).setContentType(MimeTypes.APPLICATION_M3U8).setStreamDuration(exoPlayer.getDuration() * 1000).setMetadata(metadata).build();

            RemoteMediaClient remoteMediaClient = castContext.getSessionManager().getCurrentCastSession().getRemoteMediaClient();
            remoteMediaClient.load(new MediaLoadRequestData.Builder().setCustomData(jsonObject).setMediaInfo(mediaInfo).build());

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
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCastSessionAvailable() {
        switchCurrentPlayer(castPlayer);
        startPlayback();
    }

    @Override
    public void onCastSessionUnavailable() {
        switchCurrentPlayer(exoPlayer);
        startPlayback();
    }

}