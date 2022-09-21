package de.nidoca.webview.iptv;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.exoplayer2.DeviceInfo;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.VideoSize;
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
import androidx.annotation.Nullable;
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
        exoPlayer.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {
                AnalyticsListener.super.onPlayerStateChanged(eventTime, playWhenReady, playbackState);
            }

            @Override
            public void onPlaybackStateChanged(EventTime eventTime, int state) {
                AnalyticsListener.super.onPlaybackStateChanged(eventTime, state);
            }

            @Override
            public void onPlayWhenReadyChanged(EventTime eventTime, boolean playWhenReady, int reason) {
                AnalyticsListener.super.onPlayWhenReadyChanged(eventTime, playWhenReady, reason);
            }

            @Override
            public void onPlaybackSuppressionReasonChanged(EventTime eventTime, int playbackSuppressionReason) {
                AnalyticsListener.super.onPlaybackSuppressionReasonChanged(eventTime, playbackSuppressionReason);
            }

            @Override
            public void onIsPlayingChanged(EventTime eventTime, boolean isPlaying) {
                AnalyticsListener.super.onIsPlayingChanged(eventTime, isPlaying);
            }

            @Override
            public void onTimelineChanged(EventTime eventTime, int reason) {
                AnalyticsListener.super.onTimelineChanged(eventTime, reason);
            }

            @Override
            public void onMediaItemTransition(EventTime eventTime, @Nullable MediaItem mediaItem, int reason) {
                AnalyticsListener.super.onMediaItemTransition(eventTime, mediaItem, reason);
            }

            @Override
            public void onPositionDiscontinuity(EventTime eventTime, int reason) {
                AnalyticsListener.super.onPositionDiscontinuity(eventTime, reason);
            }

            @Override
            public void onPositionDiscontinuity(EventTime eventTime, Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                AnalyticsListener.super.onPositionDiscontinuity(eventTime, oldPosition, newPosition, reason);
            }

            @Override
            public void onSeekStarted(EventTime eventTime) {
                AnalyticsListener.super.onSeekStarted(eventTime);
            }

            @Override
            public void onSeekProcessed(EventTime eventTime) {
                AnalyticsListener.super.onSeekProcessed(eventTime);
            }

            @Override
            public void onPlaybackParametersChanged(EventTime eventTime, PlaybackParameters playbackParameters) {
                AnalyticsListener.super.onPlaybackParametersChanged(eventTime, playbackParameters);
            }

            @Override
            public void onSeekBackIncrementChanged(EventTime eventTime, long seekBackIncrementMs) {
                AnalyticsListener.super.onSeekBackIncrementChanged(eventTime, seekBackIncrementMs);
            }

            @Override
            public void onSeekForwardIncrementChanged(EventTime eventTime, long seekForwardIncrementMs) {
                AnalyticsListener.super.onSeekForwardIncrementChanged(eventTime, seekForwardIncrementMs);
            }

            @Override
            public void onMaxSeekToPreviousPositionChanged(EventTime eventTime, long maxSeekToPreviousPositionMs) {
                AnalyticsListener.super.onMaxSeekToPreviousPositionChanged(eventTime, maxSeekToPreviousPositionMs);
            }

            @Override
            public void onRepeatModeChanged(EventTime eventTime, int repeatMode) {
                AnalyticsListener.super.onRepeatModeChanged(eventTime, repeatMode);
            }

            @Override
            public void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {
                AnalyticsListener.super.onShuffleModeChanged(eventTime, shuffleModeEnabled);
            }

            @Override
            public void onIsLoadingChanged(EventTime eventTime, boolean isLoading) {
                AnalyticsListener.super.onIsLoadingChanged(eventTime, isLoading);
            }

            @Override
            public void onLoadingChanged(EventTime eventTime, boolean isLoading) {
                AnalyticsListener.super.onLoadingChanged(eventTime, isLoading);
            }

            @Override
            public void onAvailableCommandsChanged(EventTime eventTime, Player.Commands availableCommands) {
                AnalyticsListener.super.onAvailableCommandsChanged(eventTime, availableCommands);
            }

            @Override
            public void onPlayerError(EventTime eventTime, PlaybackException error) {
                AnalyticsListener.super.onPlayerError(eventTime, error);
            }

            @Override
            public void onPlayerErrorChanged(EventTime eventTime, @Nullable PlaybackException error) {
                AnalyticsListener.super.onPlayerErrorChanged(eventTime, error);
            }

            @Override
            public void onTracksChanged(EventTime eventTime, Tracks tracks) {
                AnalyticsListener.super.onTracksChanged(eventTime, tracks);
            }

            @Override
            public void onTrackSelectionParametersChanged(EventTime eventTime, TrackSelectionParameters trackSelectionParameters) {
                AnalyticsListener.super.onTrackSelectionParametersChanged(eventTime, trackSelectionParameters);
            }

            @Override
            public void onMediaMetadataChanged(EventTime eventTime, com.google.android.exoplayer2.MediaMetadata mediaMetadata) {
                AnalyticsListener.super.onMediaMetadataChanged(eventTime, mediaMetadata);
            }

            @Override
            public void onPlaylistMetadataChanged(EventTime eventTime, com.google.android.exoplayer2.MediaMetadata playlistMetadata) {
                AnalyticsListener.super.onPlaylistMetadataChanged(eventTime, playlistMetadata);
            }

            @Override
            public void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                AnalyticsListener.super.onLoadStarted(eventTime, loadEventInfo, mediaLoadData);
            }

            @Override
            public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                AnalyticsListener.super.onLoadCompleted(eventTime, loadEventInfo, mediaLoadData);
            }

            @Override
            public void onLoadCanceled(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                AnalyticsListener.super.onLoadCanceled(eventTime, loadEventInfo, mediaLoadData);
            }

            @Override
            public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
                AnalyticsListener.super.onLoadError(eventTime, loadEventInfo, mediaLoadData, error, wasCanceled);
            }

            @Override
            public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
                AnalyticsListener.super.onDownstreamFormatChanged(eventTime, mediaLoadData);
            }

            @Override
            public void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {
                AnalyticsListener.super.onUpstreamDiscarded(eventTime, mediaLoadData);
            }

            @Override
            public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
                AnalyticsListener.super.onBandwidthEstimate(eventTime, totalLoadTimeMs, totalBytesLoaded, bitrateEstimate);
            }

            @Override
            public void onMetadata(EventTime eventTime, Metadata metadata) {
                AnalyticsListener.super.onMetadata(eventTime, metadata);
            }

            @Override
            public void onCues(EventTime eventTime, List<Cue> cues) {
                AnalyticsListener.super.onCues(eventTime, cues);
            }

            @Override
            public void onCues(EventTime eventTime, CueGroup cueGroup) {
                AnalyticsListener.super.onCues(eventTime, cueGroup);
            }

            @Override
            public void onDecoderEnabled(EventTime eventTime, int trackType, DecoderCounters decoderCounters) {
                AnalyticsListener.super.onDecoderEnabled(eventTime, trackType, decoderCounters);
            }

            @Override
            public void onDecoderInitialized(EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {
                AnalyticsListener.super.onDecoderInitialized(eventTime, trackType, decoderName, initializationDurationMs);
            }

            @Override
            public void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {
                AnalyticsListener.super.onDecoderInputFormatChanged(eventTime, trackType, format);
            }

            @Override
            public void onDecoderDisabled(EventTime eventTime, int trackType, DecoderCounters decoderCounters) {
                AnalyticsListener.super.onDecoderDisabled(eventTime, trackType, decoderCounters);
            }

            @Override
            public void onAudioEnabled(EventTime eventTime, DecoderCounters decoderCounters) {
                AnalyticsListener.super.onAudioEnabled(eventTime, decoderCounters);
            }

            @Override
            public void onAudioDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
                AnalyticsListener.super.onAudioDecoderInitialized(eventTime, decoderName, initializedTimestampMs, initializationDurationMs);
            }

            @Override
            public void onAudioDecoderInitialized(EventTime eventTime, String decoderName, long initializationDurationMs) {
                AnalyticsListener.super.onAudioDecoderInitialized(eventTime, decoderName, initializationDurationMs);
            }

            @Override
            public void onAudioInputFormatChanged(EventTime eventTime, Format format) {
                AnalyticsListener.super.onAudioInputFormatChanged(eventTime, format);
            }

            @Override
            public void onAudioInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
                AnalyticsListener.super.onAudioInputFormatChanged(eventTime, format, decoderReuseEvaluation);
            }

            @Override
            public void onAudioPositionAdvancing(EventTime eventTime, long playoutStartSystemTimeMs) {
                AnalyticsListener.super.onAudioPositionAdvancing(eventTime, playoutStartSystemTimeMs);
            }

            @Override
            public void onAudioUnderrun(EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
                AnalyticsListener.super.onAudioUnderrun(eventTime, bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
            }

            @Override
            public void onAudioDecoderReleased(EventTime eventTime, String decoderName) {
                AnalyticsListener.super.onAudioDecoderReleased(eventTime, decoderName);
            }

            @Override
            public void onAudioDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
                AnalyticsListener.super.onAudioDisabled(eventTime, decoderCounters);
            }

            @Override
            public void onAudioSessionIdChanged(EventTime eventTime, int audioSessionId) {
                AnalyticsListener.super.onAudioSessionIdChanged(eventTime, audioSessionId);
            }

            @Override
            public void onAudioAttributesChanged(EventTime eventTime, AudioAttributes audioAttributes) {
                AnalyticsListener.super.onAudioAttributesChanged(eventTime, audioAttributes);
            }

            @Override
            public void onSkipSilenceEnabledChanged(EventTime eventTime, boolean skipSilenceEnabled) {
                AnalyticsListener.super.onSkipSilenceEnabledChanged(eventTime, skipSilenceEnabled);
            }

            @Override
            public void onAudioSinkError(EventTime eventTime, Exception audioSinkError) {
                AnalyticsListener.super.onAudioSinkError(eventTime, audioSinkError);
            }

            @Override
            public void onAudioCodecError(EventTime eventTime, Exception audioCodecError) {
                AnalyticsListener.super.onAudioCodecError(eventTime, audioCodecError);
            }

            @Override
            public void onVolumeChanged(EventTime eventTime, float volume) {
                AnalyticsListener.super.onVolumeChanged(eventTime, volume);
            }

            @Override
            public void onDeviceInfoChanged(EventTime eventTime, DeviceInfo deviceInfo) {
                AnalyticsListener.super.onDeviceInfoChanged(eventTime, deviceInfo);
            }

            @Override
            public void onDeviceVolumeChanged(EventTime eventTime, int volume, boolean muted) {
                AnalyticsListener.super.onDeviceVolumeChanged(eventTime, volume, muted);
            }

            @Override
            public void onVideoEnabled(EventTime eventTime, DecoderCounters decoderCounters) {
                AnalyticsListener.super.onVideoEnabled(eventTime, decoderCounters);
            }

            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
                AnalyticsListener.super.onVideoDecoderInitialized(eventTime, decoderName, initializedTimestampMs, initializationDurationMs);
            }

            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializationDurationMs) {
                AnalyticsListener.super.onVideoDecoderInitialized(eventTime, decoderName, initializationDurationMs);
            }

            @Override
            public void onVideoInputFormatChanged(EventTime eventTime, Format format) {
                AnalyticsListener.super.onVideoInputFormatChanged(eventTime, format);
            }

            @Override
            public void onVideoInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
                AnalyticsListener.super.onVideoInputFormatChanged(eventTime, format, decoderReuseEvaluation);
            }

            @Override
            public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
                AnalyticsListener.super.onDroppedVideoFrames(eventTime, droppedFrames, elapsedMs);
            }

            @Override
            public void onVideoDecoderReleased(EventTime eventTime, String decoderName) {
                AnalyticsListener.super.onVideoDecoderReleased(eventTime, decoderName);
            }

            @Override
            public void onVideoDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
                AnalyticsListener.super.onVideoDisabled(eventTime, decoderCounters);
            }

            @Override
            public void onVideoFrameProcessingOffset(EventTime eventTime, long totalProcessingOffsetUs, int frameCount) {
                AnalyticsListener.super.onVideoFrameProcessingOffset(eventTime, totalProcessingOffsetUs, frameCount);
            }

            @Override
            public void onVideoCodecError(EventTime eventTime, Exception videoCodecError) {
                AnalyticsListener.super.onVideoCodecError(eventTime, videoCodecError);
            }

            @Override
            public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
                AnalyticsListener.super.onRenderedFirstFrame(eventTime, output, renderTimeMs);
            }

            @Override
            public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
                AnalyticsListener.super.onVideoSizeChanged(eventTime, videoSize);
            }

            @Override
            public void onVideoSizeChanged(EventTime eventTime, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                AnalyticsListener.super.onVideoSizeChanged(eventTime, width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
            }

            @Override
            public void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {
                AnalyticsListener.super.onSurfaceSizeChanged(eventTime, width, height);
            }

            @Override
            public void onDrmSessionAcquired(EventTime eventTime) {
                AnalyticsListener.super.onDrmSessionAcquired(eventTime);
            }

            @Override
            public void onDrmSessionAcquired(EventTime eventTime, int state) {
                AnalyticsListener.super.onDrmSessionAcquired(eventTime, state);
            }

            @Override
            public void onDrmKeysLoaded(EventTime eventTime) {
                AnalyticsListener.super.onDrmKeysLoaded(eventTime);
            }

            @Override
            public void onDrmSessionManagerError(EventTime eventTime, Exception error) {
                AnalyticsListener.super.onDrmSessionManagerError(eventTime, error);
            }

            @Override
            public void onDrmKeysRestored(EventTime eventTime) {
                AnalyticsListener.super.onDrmKeysRestored(eventTime);
            }

            @Override
            public void onDrmKeysRemoved(EventTime eventTime) {
                AnalyticsListener.super.onDrmKeysRemoved(eventTime);
            }

            @Override
            public void onDrmSessionReleased(EventTime eventTime) {
                AnalyticsListener.super.onDrmSessionReleased(eventTime);
            }

            @Override
            public void onPlayerReleased(EventTime eventTime) {
                AnalyticsListener.super.onPlayerReleased(eventTime);
            }

            @Override
            public void onEvents(Player player, Events events) {
                AnalyticsListener.super.onEvents(player, events);

            }
        });
        //exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player player, Player.Events events) {
                Player.Listener.super.onEvents(player, events);

                String a = "";
                int size = events.size();
                for(int i =0;i<size;i++){
                   a="-----------           " + events.get(i);
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


            long duration = exoPlayer.getDuration();
            System.out.println("duration exoplayer: " + duration);
            long durationCast = castPlayer.getDuration();
            System.out.println("duration cast: " + durationCast);
            MediaInfo mediaInfo = new MediaInfo.Builder(this.mediaItem.localConfiguration.uri.toString()).setStreamType(MediaInfo.STREAM_TYPE_LIVE).setCustomData(jsonObject).setContentType(MimeTypes.APPLICATION_M3U8).setStreamDuration(durationCast * 1000).setMetadata(metadata).build();


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