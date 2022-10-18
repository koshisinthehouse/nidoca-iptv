package de.nidoca.webview.iptv;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;
import com.google.common.io.ByteStreams;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import de.nidoca.webview.iptv.databinding.ActivityMainBinding;
import de.nidoca.webview.iptv.m3u.Entry;
import de.nidoca.webview.iptv.m3u.Parser;
import de.nidoca.webview.iptv.view.StationArrayAdapter;

import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements SessionManagerListener<CastSession> {


    private final String appM3UFileName = "app.m3u";

    private final ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
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
                        Toast.makeText(this, getResources().getText(R.string.station_list_loaded_successfully), Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        Toast.makeText(MainActivity.this, getResources().getText(R.string.error_station_list_file_load_data), Toast.LENGTH_SHORT).show();
                    }
                }
            });


    private ActivityMainBinding binding;

    private Entry entry;

    // the local and remote players
    private ExoPlayer exoPlayer = null;
    private CastPlayer castPlayer = null;
    private Player currentPlayer = null;
    private StyledPlayerView exoPlayerView;

    // the Cast context
    private CastContext castContext;
    private CastSession castSession;
    private SessionManager castSessionManager;
    private MenuItem castMenuItem;

    public void initStations() {
        File directory = getFilesDir();
        File appM3UFile = new File(directory, appM3UFileName);
        if (!appM3UFile.exists()) {
            try {
                if (!appM3UFile.createNewFile()) {
                    Toast.makeText(this, getResources().getText(R.string.error_station_list_file_create_new_file), Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(this, getResources().getText(R.string.error_station_list_file_create_new_file), Toast.LENGTH_SHORT).show();
            }
        }
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = openFileInput(appM3UFileName);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, getResources().getText(R.string.error_station_list_file_not_found), Toast.LENGTH_SHORT).show();
        }
        Parser parser = new Parser();
        List<Entry> entries = parser.parse(fileInputStream);
        ArrayAdapter<Entry> adapter = new StationArrayAdapter(MainActivity.this,
                android.R.layout.simple_list_item_1,
                entries);
        binding.stationList.setAdapter(adapter);
    }

    @Override
    protected void onPause() {
        if (this.currentPlayer == this.exoPlayer) {
            this.currentPlayer.stop();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (this.currentPlayer == this.exoPlayer) {
            this.currentPlayer.stop();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onStop();
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
        if (this.currentPlayer == this.exoPlayer) {
            this.currentPlayer.prepare();
        }
        super.onResume();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //exoPlayerView - start
        exoPlayerView = binding.exoPlayerView;
        binding.exoPlayerView.getLayoutParams().height = calculateCurrentPlayerHeight();
        SubtitleView subtitleView = exoPlayerView.getSubtitleView();
        if (subtitleView != null)
            subtitleView.setVisibility(View.GONE);
        exoPlayerView.setControllerAutoShow(false);
        exoPlayerView.setFullscreenButtonClickListener(isFullScreen -> {
            if (isFullScreen) {
                binding.stationList.setVisibility(View.GONE);
                binding.toolbarLayout.setVisibility(View.GONE);
                binding.exoPlayerView.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                final float scale = getResources().getDisplayMetrics().density;
                binding.exoPlayerView.getLayoutParams().height = calculateCurrentPlayerHeight();
                binding.stationList.setVisibility(View.VISIBLE);
                binding.toolbarLayout.setVisibility(View.VISIBLE);
            }
        });
        //exoPlayerView - end

        //castPlayer - start
        CastContext
                .getSharedInstance(getApplicationContext(), ContextCompat.getMainExecutor(this)).addOnCompleteListener(task -> {
                    castContext = task.getResult();
                    castPlayer = new CastPlayer(castContext);
                    castPlayer.setPlayWhenReady(true);
                    castSessionManager = castContext.getSessionManager();
                    castSession = castSessionManager.getCurrentCastSession();
                    castSessionManager.addSessionManagerListener(this, CastSession.class);
                });
        //castPlayer - end

        //exoPlayer - start
        //must init after CastContext
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {

                // Update UI using current tracks.
            }
        });
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
        binding.stationList.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = (Entry) parent.getItemAtPosition(position);
            this.entry = entry;
            startPlayback();
        });
        initStations();
        //listView - end


    }

    /**
     * calculate exo player view height, depending on device screen size.
     *
     * @return calculated height of exo player view.
     */
    private int calculateCurrentPlayerHeight() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        return (int) ((Math.min(width, height)) * 0.5675);
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
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            toolbar.setTitle("");
            return;

        }

        String tvgId = entry.getTvgId();
        String tvgLogo = entry.getTvgLogo();
        String tvgName = entry.getTvgName();
        String channelName = entry.getChannelName();
        String channelUrl = entry.getChannelUri();
        Uri channelUri = Uri.parse(channelUrl);
        String mimeType = MimeTypes.APPLICATION_M3U8;
        if (!channelUrl.endsWith("m3u8")) {
            //https://github.com/google/ExoPlayer/issues/90
            mimeType = MimeTypes.VIDEO_MP2T;
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(tvgName);

        if (currentPlayer == exoPlayer) {
            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder();
            mediaItemBuilder.setUri(channelUrl);
            mediaItemBuilder.setMimeType(mimeType);
            /**
             mediaItemBuilder.setClippingConfiguration(
             new MediaItem.ClippingConfiguration.Builder()
             .setStartPositionMs(0l)
             .setEndPositionMs(20000000000000l)
             .build());
             */
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
            if (tvgLogo != null && Patterns.WEB_URL.matcher(tvgLogo).matches()) {
                metadata.addImage(new WebImage(Uri.parse(tvgLogo)));
            }
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

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_upload) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            mStartForResult.launch(intent);
            return true;
        }

        if (id == R.id.action_info) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://raw.githubusercontent.com/koshisinthehouse/nidoca-iptv/main/publish/imprint.html"));
            startActivity(browserIntent);
        }

        if (id == R.id.action_url_import_checked) {
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
                        Toast.makeText(MainActivity.this, getResources().getText(R.string.error_station_list_load_url), Toast.LENGTH_SHORT).show();
                        throw new RuntimeException(e);
                    }
                    handler.post(() -> {
                        ArrayAdapter<Entry> adapter = new StationArrayAdapter(MainActivity.this,
                                android.R.layout.simple_list_item_1,
                                entries);
                        binding.stationList.setAdapter(adapter);
                        Toast.makeText(MainActivity.this, getResources().getText(R.string.station_list_loaded_successfully), Toast.LENGTH_SHORT).show();
                    });
                });
            }

            Toolbar toolbar = findViewById(R.id.toolbar);
            MenuItem actionUrlImportChecked = toolbar.getMenu().findItem(R.id.action_url_import_checked);
            actionUrlImportChecked.setVisible(false);

            MenuItem actionUrlImport = toolbar.getMenu().findItem(R.id.action_url_import);
            actionUrlImport.setVisible(true);

            binding.m3UrlText.setVisibility(View.GONE);

            return true;
        }

        if (id == R.id.action_url_import) {

            Toolbar toolbar = findViewById(R.id.toolbar);
            MenuItem actionUrlImportChecked = toolbar.getMenu().findItem(R.id.action_url_import_checked);
            actionUrlImportChecked.setVisible(true);

            MenuItem actionUrlImport = toolbar.getMenu().findItem(R.id.action_url_import);
            actionUrlImport.setVisible(false);

            binding.m3UrlText.setVisibility(View.VISIBLE);

            return true;
        }

        if (id == R.id.action_info) {
            //TODO:
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSessionEnded(@NonNull CastSession castSession, int i) {
        switchCurrentPlayer(exoPlayer);
        this.exoPlayerView.setVisibility(View.VISIBLE);
        startPlayback();

    }

    @Override
    public void onSessionEnding(@NonNull CastSession castSession) {
        this.exoPlayerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSessionResumeFailed(@NonNull CastSession castSession, int i) {
    }

    @Override
    public void onSessionResumed(@NonNull CastSession castSession, boolean b) {
    }

    @Override
    public void onSessionResuming(@NonNull CastSession castSession, @NonNull String s) {
    }

    @Override
    public void onSessionStartFailed(@NonNull CastSession castSession, int i) {
    }

    @Override
    public void onSessionStarted(@NonNull CastSession castSession, @NonNull String s) {
        switchCurrentPlayer(castPlayer);
        startPlayback();
    }

    @Override
    public void onSessionStarting(@NonNull CastSession castSession) {
        this.exoPlayerView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onSessionSuspended(@NonNull CastSession castSession, int i) {
    }

}