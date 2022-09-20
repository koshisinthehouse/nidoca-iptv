package de.nidoca.webview.iptv;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.util.IOUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.common.io.ByteStreams;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.navigation.ui.AppBarConfiguration;

import de.nidoca.webview.iptv.databinding.ActivityMainBinding;
import de.nidoca.webview.iptv.m3u.Entry;
import de.nidoca.webview.iptv.m3u.LoadM3U;
import de.nidoca.webview.iptv.m3u.Parser;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements SessionAvailabilityListener {


    private String appM3UFileName = "app.m3u";

    private ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intent = result.getData();
                    String action = intent.getAction();
                    Uri uri = intent.getData();
                    try (InputStream inputStream = getContentResolver().openInputStream(uri);) {
                        FileOutputStream fos = openFileOutput(appM3UFileName, Context.MODE_PRIVATE);
                        fos.write(ByteStreams.toByteArray(inputStream));
                        fos.close();
                        initStations();
                    } catch (IOException e) {
                        Toast.makeText(MainActivity.this, "error reading file", Toast.LENGTH_SHORT);
                    }
                }
            });


    private AppBarConfiguration appBarConfiguration;

    private ActivityMainBinding binding;

    private MediaItem mediaItem;

    private ListView listView;

    // the local and remote players
    private ExoPlayer exoPlayer = null;
    private CastPlayer castPlayer = null;
    private Player currentPlayer = null;
    private StyledPlayerView exoPlayerView;
    private ViewGroup playerViewParent;

    // the Cast context
    private MenuItem castMenuItem;
    private CastContext castContext;
    private CastSession castSession;
    private SessionManager castSessionManager;
    private SessionManagerListener<CastSession> sessionManagerListener =
            new SessionManagerListenerImpl();

    private class SessionManagerListenerImpl implements SessionManagerListener<CastSession> {
        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
        }

        @Override
        public void onSessionStarting(@NonNull CastSession castSession) {

        }

        @Override
        public void onSessionSuspended(@NonNull CastSession castSession, int i) {

        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
        }

        @Override
        public void onSessionResuming(@NonNull CastSession castSession, @NonNull String s) {

        }

        @Override
        public void onSessionStartFailed(@NonNull CastSession castSession, int i) {

        }

        @Override
        public void onSessionEnded(CastSession session, int error) {
            finish();
        }

        @Override
        public void onSessionEnding(@NonNull CastSession castSession) {

        }

        @Override
        public void onSessionResumeFailed(@NonNull CastSession castSession, int i) {

        }
    }

    public void initStations() {
        File directory = getFilesDir();
        File appM3UFile = new File(directory, appM3UFileName);
        if (!appM3UFile.exists()) {
            try {
                if (!appM3UFile.createNewFile()) {
                    Toast.makeText(this, "Fehler beim erstellen der Station Liste", Toast.LENGTH_SHORT);
                }
            } catch (IOException e) {
                Toast.makeText(this, "Fehler beim erstellen der Station Liste", Toast.LENGTH_SHORT);
            }
        }
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = openFileInput(appM3UFileName);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Fehler beim öffnen der station datei", Toast.LENGTH_SHORT);
        }
        Parser parser = new Parser();
        List<Entry> entries = parser.parse(fileInputStream);
        ArrayAdapter<Entry> adapter = new ArrayAdapter<Entry>(MainActivity.this,
                android.R.layout.simple_list_item_1,
                entries);
        listView.setAdapter(adapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (this.currentPlayer == this.exoPlayer) {
            this.currentPlayer.stop();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.currentPlayer == this.exoPlayer) {
            this.currentPlayer.stop();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.exoPlayer.release();
        this.castPlayer.release();
        this.currentPlayer = null;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.currentPlayer == this.exoPlayer) {
            this.currentPlayer.prepare();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

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
                .getSharedInstance(getApplicationContext(), Executors.newSingleThreadExecutor()).addOnCompleteListener(new OnCompleteListener<CastContext>() {
                    @Override
                    public void onComplete(@NonNull Task<CastContext> task) {
                        castContext = task.getResult();
                        castPlayer = new CastPlayer(castContext);
                        castPlayer.setPlayWhenReady(true);
                        castPlayer.setSessionAvailabilityListener(MainActivity.this);
                        castSessionManager = castContext.getSessionManager();
                        castSession = castSessionManager.getCurrentCastSession();
                        //WENN Session Manager dann kaputt muss auch removed werden ? https://developers.google.com/cast/docs/android_sender/integrate
                        //castSessionManager.addSessionManagerListener(sessionManagerListener, CastSession.class);
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
        listView = binding.list;
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = (Entry) parent.getItemAtPosition(position);
            com.google.android.exoplayer2.MediaMetadata mediaMetadata = new com.google.android.exoplayer2.MediaMetadata.Builder().setTitle(entry.getTvgName()).setSubtitle(entry.getTvgId()).setStation(entry.getChannelName()).build();
            this.mediaItem = new MediaItem.Builder().setUri(entry.getChannelUri()).setMediaMetadata(mediaMetadata).setTag(null).build();
            startPlayback();
        });
        initStations();
        //listView - end


    }

    private void switchCurrentPlayer(Player newPlayer) {

        if (this.currentPlayer == newPlayer) {
            return;
        }

        if (this.currentPlayer != null) {
            newPlayer.seekTo(this.currentPlayer.getCurrentMediaItemIndex(), this.currentPlayer.getCurrentPosition());
            currentPlayer.stop();
        }

        this.currentPlayer = newPlayer;

        if (currentPlayer == this.castPlayer) {
            //exoPlayerView.setVisibility(View.INVISIBLE);
        } else {
            //exoPlayerView.setVisibility(View.VISIBLE);
        }

    }

    /**
     * Sets the video on the current player (local or remote), whichever is active.
     */
    private void startPlayback() {

        if (this.mediaItem == null) {
            return;

        }

        if (currentPlayer == exoPlayer) {
            exoPlayer.setMediaItem(this.mediaItem);
            exoPlayer.prepare();
        }

        if (castPlayer == currentPlayer) {

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


            MediaInfo mediaInfo = new MediaInfo.Builder(this.mediaItem.localConfiguration.uri.toString()).setStreamType(MediaInfo.STREAM_TYPE_LIVE).setCustomData(jsonObject).setContentType(MimeTypes.APPLICATION_M3U8).setStreamDuration(exoPlayer.getDuration() * 1000).setMetadata(metadata).build();

            RemoteMediaClient remoteMediaClient = castContext.getSessionManager().getCurrentCastSession().getRemoteMediaClient();
            remoteMediaClient.load(new MediaLoadRequestData.Builder().setCustomData(jsonObject).setMediaInfo(mediaInfo).build());

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        castMenuItem = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_upload) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("file/*");
            mStartForResult.launch(intent);
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
        //startPlayback();
    }

}