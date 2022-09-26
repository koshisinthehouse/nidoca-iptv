package de.nidoca.webview.iptv;


import static com.google.android.exoplayer2.C.DATA_TYPE_MEDIA;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.android.exoplayer2.DeviceInfo;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;

import com.google.android.exoplayer2.MediaItem.DrmConfiguration;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadOptions;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.navigation.ui.AppBarConfiguration;

import de.nidoca.webview.iptv.databinding.ActivityMainBinding;
import de.nidoca.webview.iptv.m3u.Entry;
import de.nidoca.webview.iptv.m3u.LoadImage;
import de.nidoca.webview.iptv.m3u.LoadM3U;
import de.nidoca.webview.iptv.m3u.Parser;
import de.nidoca.webview.iptv.view.StationArrayAdapter;

import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
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

    private Entry entry;

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
        ArrayAdapter<Entry> adapter = new StationArrayAdapter(MainActivity.this,
                android.R.layout.simple_list_item_1,
                entries);
        binding.list.setAdapter(adapter);


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

        ImageView iv = findViewById(R.id.m3u_url_save_btn);
        iv.setOnClickListener(view -> {
            EditText editText = findViewById(R.id.m3_url_text);
            String urlAsString = editText.getText().toString();
            if (!Patterns.WEB_URL.matcher(urlAsString).matches()) {
                //TODO: show validation errors
            } else {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Looper mainLooper = Looper.getMainLooper();
                Handler handler = new Handler(mainLooper);
                executor.execute(() -> {
                    List<Entry> entries;
                    try {
                        URL url = new URL(urlAsString);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(10000);
                        Parser parser = new Parser();
                        InputStream inputStream = conn.getInputStream();
                        entries = parser.parse(inputStream);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Fehler beim Laden der M3U URL", Toast.LENGTH_SHORT).show();
                        throw new RuntimeException(e);
                    }
                    handler.post(() -> {
                        ArrayAdapter<Entry> adapter = new StationArrayAdapter(MainActivity.this,
                                android.R.layout.simple_list_item_1,
                                entries);
                        binding.list.setAdapter(adapter);
                        binding.m3uUrlLayout.setVisibility(View.GONE);
                    });
                });
            }
        });

        //exoPlayerView - start
        exoPlayerView = binding.playerView;
        playerViewParent = (ViewGroup) exoPlayerView.getParent();
        exoPlayerView.setControllerAutoShow(false);
        exoPlayerView.setFullscreenButtonClickListener(isFullScreen -> {
            if (isFullScreen) {
                LinearLayout linearLayout = new LinearLayout(this);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                ((ViewGroup) exoPlayerView.getParent()).removeView(exoPlayerView);
                //linearLayout.addView(exoPlayerView);
                setContentView(exoPlayerView);
                //binding.getRoot().setOrientation(LinearLayout.VERTICAL);
                exoPlayerView.setRotation(90);
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
                .getSharedInstance(getApplicationContext(), Executors.newSingleThreadExecutor()).addOnCompleteListener(task -> {
                    castContext = task.getResult();
                    castPlayer = new CastPlayer(castContext);
                    castPlayer.setPlayWhenReady(true);
                    castPlayer.setSessionAvailabilityListener(MainActivity.this);
                    castSessionManager = castContext.getSessionManager();
                    castSession = castSessionManager.getCurrentCastSession();
                });
        //castPlayer - end

        //exoPlayer - start
        //must init after CastContext
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setPlayWhenReady(true);
        //exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player player, Player.Events events) {
                Player.Listener.super.onEvents(player, events);

                String a = "";
                int size = events.size();
                for (int i = 0; i < size; i++) {
                    a = "-----------           " + events.get(i);
                }
                System.err.println(events.toString());
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
        binding.list.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = (Entry) parent.getItemAtPosition(position);
            this.entry = entry;
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
            long currentPosition = this.currentPlayer.getCurrentPosition();
            System.out.println("current position: " + currentPosition);
            newPlayer.seekTo(this.currentPlayer.getCurrentMediaItemIndex(), currentPosition);
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

        if (this.entry == null) {
            return;

        }

        String tvgId = entry.getTvgId();
        String tvgName = entry.getTvgName();
        String channelName = entry.getChannelName();
        String channelUrl = entry.getChannelUri();
        Uri channelUri = Uri.parse(channelUrl);
        String mimeType = MimeTypes.APPLICATION_M3U8;
        if (!channelUrl.endsWith("m3u8")) {
            //https://github.com/google/ExoPlayer/issues/90
            mimeType = MimeTypes.VIDEO_MP2T;
        }

        System.err.println(entry);

        if (currentPlayer == exoPlayer) {
            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder();
            mediaItemBuilder.setUri(channelUrl);
            mediaItemBuilder.setMimeType(mimeType);
            mediaItemBuilder.setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0l)
                            .setEndPositionMs(20000000000000l)
                            .build());

            new MediaItem.SubtitleConfiguration.Builder(channelUri)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLabel(channelName)
                    .build();

            MediaItem mediaItem = mediaItemBuilder.build();
            DefaultMediaSourceFactory defaultMediaSourceFactory = new DefaultMediaSourceFactory(this);
            MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

            /**
             mediaSource.addEventListener(new Handler() {
             }, new MediaSourceEventListener() {
            @Override public void onLoadCompleted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
            MediaSourceEventListener.super.onLoadCompleted(windowIndex, mediaPeriodId, loadEventInfo, new MediaLoadData(DATA_TYPE_MEDIA));
            System.out.println(mediaLoadData.mediaEndTimeMs);
            }
            });
             mediaSource.addDrmEventListener(new Handler(), new DrmSessionEventListener() {
            @Override public void onDrmSessionAcquired(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, int state) {
            DrmSessionEventListener.super.onDrmSessionAcquired(windowIndex, mediaPeriodId, state);
            }
            });
             */

            exoPlayer.setMediaSource(mediaSource);

            exoPlayer.prepare();
        }

        if (castPlayer == currentPlayer) {
            MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
            metadata.putString(MediaMetadata.KEY_TITLE, tvgId);
            metadata.putString(MediaMetadata.KEY_SUBTITLE, tvgName);
            //metadata.addImage(WebImage(Uri.parse("any-image-url")));

            MediaInfo.Builder mediaInfoBuilder = new MediaInfo.Builder(channelUrl);
            mediaInfoBuilder.setStreamType(MediaInfo.STREAM_TYPE_LIVE);
            mediaInfoBuilder.setContentType(mimeType);
            mediaInfoBuilder.setStreamDuration(castPlayer.getDuration() * 1000);
            mediaInfoBuilder.setMetadata(metadata);
            JSONObject jsonObject = new JSONObject();
            //ISSUE - start
            //https://github.com/google/ExoPlayer/issues/10591
            try {
                JSONObject mediaItem = new JSONObject();
                mediaItem.put("uri", channelUrl);
                mediaItem.put("mediaId", tvgId);
                jsonObject.put("mediaItem", mediaItem);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mediaInfoBuilder.setCustomData(jsonObject);
            //ISSUE - end

            MediaInfo mediaInfo = mediaInfoBuilder.build();

            RemoteMediaClient remoteMediaClient = castContext.getSessionManager().getCurrentCastSession().getRemoteMediaClient();
            MediaLoadOptions.Builder mediaLoadOptions = new MediaLoadOptions.Builder();
            mediaLoadOptions.setCustomData(jsonObject);
            remoteMediaClient.load(mediaInfo, mediaLoadOptions.build());

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
            intent.setType("*/*");
            mStartForResult.launch(intent);
            return true;
        }
        if (id == R.id.action_url_input) {
            if (binding.m3uUrlLayout.getVisibility() == View.VISIBLE) {
                binding.m3uUrlLayout.setVisibility(View.GONE);
            } else {
                binding.m3uUrlLayout.setVisibility(View.VISIBLE);
            }
            return true;
        }
        if (id == R.id.action_info) {
            //TODO:
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCastSessionAvailable() {
        switchCurrentPlayer(castPlayer);
        this.exoPlayerView.setVisibility(View.INVISIBLE);
        startPlayback();
    }

    @Override
    public void onCastSessionUnavailable() {
        switchCurrentPlayer(exoPlayer);
        this.exoPlayerView.setVisibility(View.VISIBLE);
        startPlayback();
    }

}