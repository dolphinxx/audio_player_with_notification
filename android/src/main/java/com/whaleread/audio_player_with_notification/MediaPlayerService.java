package com.whaleread.audio_player_with_notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MediaPlayerService extends Service implements Runnable {
    @SuppressWarnings("ConstantConditions")
    public static final String BROADCAST_TO_SERVICE = MediaPlayerService.class.getPackage().getName() + ".broadcastToService";
    @SuppressWarnings("ConstantConditions")
    public static final String SERVICE_TO_BROADCAST = MediaPlayerService.class.getPackage().getName() + ".serviceToBroadcast";
    public static final String POSITION_NOTIFY_INTERVAL_KEY = "positionNotifyInterval";
    public static final String AUDIO_FOCUS_KEY = "audioFocus";
    public static final String ENABLE_LOGGING_KEY = "enableLogging";
    public static final String ACTION_TYPE_KEY = "actionType";
    public static final int ACTION_TYPE_STATUS = 1;
    public static final int ACTION_TYPE_DURATION = 2;
    public static final int ACTION_TYPE_POSITION = 3;
    public static final int ACTION_TYPE_BUFFER = 4;
    public static final String PLAYER_FUNCTION_TYPE = "playerFunctionType";
    public static final String PLAYER_TRACK_URL = "trackURL";
    public static final String PLAYER_HEADERS = "headers";
    public static final String PLAYER_VOLUME = "volume";
    public static final String PLAYER_POSITION = "position";
    public static final int PLAY_MEDIA_PLAYER = 1;
    public static final int PAUSE_MEDIA_PLAYER = 2;
    public static final int RESUME_MEDIA_PLAYER = 3;
    public static final int STOP_MEDIA_PLAYER = 4;
    public static final int CHANGE_PLAYER_TRACK = 5;
    public static final int TOGGLE_MEDIA_PLAYER = 6;
    public static final int SET_VOLUME = 7;
    public static final int SEEK_TO = 8;
    public static final int UPDATE_NOTIFICATION = 9;
    public static final int UPDATE_NOTIFICATION_THEME = 10;
    public static final int UPDATE_OPTIONS = 11;
    public static final String PLAYER_STATUS_KEY = "playerCurrentStatus";
    public static final String PLAYER_STATUS_MESSAGE_KEY = "playerStatusMessage";
    public static final String PLAYER_DURATION_KEY = "playerDuration";
    public static final String PLAYER_POSITION_KEY = "playerCurrentPosition";
    public static final String PLAYER_BUFFER_KEY = "playerCurrentBuffer";
    public static final int PLAYER_STATUS_INITIAL = -1;
    public static final int PLAYER_STATUS_STOPPED = 0;
    public static final int PLAYER_STATUS_PLAYING = 1;
    public static final int PLAYER_STATUS_PAUSED = 2;
    public static final int PLAYER_STATUS_COMPLETED = 3;
    public static final int PLAYER_STATUS_ERROR = 4;

    public static final String NOTIFICATION_TITLE_KEY = "notificationTitle";
    public static final String NOTIFICATION_SUBTITLE_KEY = "notificationSubtitle";
    public static final String NOTIFICATION_TITLE_COLOR_KEY = "notificationTitleColor";
    public static final String NOTIFICATION_SUBTITLE_COLOR_KEY = "notificationSubtitleColor";
    public static final String NOTIFICATION_BACKGROUND_COLOR_KEY = "notificationBackgroundColor";

    private static final String LOGGING_LABEL = "AudioPlayer";

    private static final String DEFAULT_USER_AGENT = "ExoPlayer/2.9.6 (Android " + Build.VERSION.RELEASE + ") Mobile";

    private SimpleExoPlayer player;
//    private volatile MediaPlayer player;
    private AudioManager audioManager;
    private Handler handler = new Handler();
    private String url;
    private Map<String, String> headers;
    private long position = C.POSITION_UNSET;
    private int bufferedPercent = 0;
//    private float volume = -1;
    private long positionNotifyInterval = 200;
    private int status = PLAYER_STATUS_INITIAL;
    private boolean autoResume = false;
    private boolean audioFocus = true;
    private boolean enableLogging = false;
    private boolean durationSent = false;
    private AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;

    private int notificationId = 1;
    private RemoteViews remoteView;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationCompatBuilder;

    private AudioAttributes audioAttributes;

    private void createNotification() {
        remoteView = new RemoteViews(getPackageName(), R.layout.layout_notification_view);
        remoteView.setImageViewResource(R.id.play_btn, status == MediaPlayerService.PLAYER_STATUS_PLAYING ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

        Intent stopIntent = new Intent()
                .setAction(MediaPlayerService.BROADCAST_TO_SERVICE)
                .putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.STOP_MEDIA_PLAYER);
        PendingIntent pendingStopIntent = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteView.setOnClickPendingIntent(R.id.stop_btn, pendingStopIntent);

        Intent playIntent = new Intent()
                .setAction(MediaPlayerService.BROADCAST_TO_SERVICE)
                .putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.TOGGLE_MEDIA_PLAYER);
        PendingIntent pendingPlayIntent = PendingIntent.getBroadcast(this, 1, playIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteView.setOnClickPendingIntent(R.id.play_btn, pendingPlayIntent);

        Intent contentIntent = new Intent(this, getMainActivityClass(this));
        PendingIntent pendingContentIntent = PendingIntent.getActivity(this, 2, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String channelId = "com.whaleread/audio_player_with_notification" + System.currentTimeMillis();
        notificationCompatBuilder =
                new NotificationCompat.Builder(this.getApplicationContext(), channelId);

        notificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationCompatBuilder
                // Title for API < 16 devices.
                .setCustomContentView(remoteView)
                .setContent(remoteView)
                .setContentIntent(pendingContentIntent)
                .setSmallIcon(android.R.drawable.ic_media_play)
//                .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Sets lock-screen visibility for 25 and below. For 26 and above, lock screen
                // visibility is set in the NotificationChannel.
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        ;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "MediaPlayerService",
                    NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
            notificationCompatBuilder.setChannelId(channelId);
        }
    }

    private void updateNotification(String title, String subtitle) {
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "update notification with title[" + title + "], subtitle[" + subtitle + "]");
        }
        if (title != null) {
            remoteView.setTextViewText(R.id.title, title);
        }
        if (subtitle != null) {
            remoteView.setTextViewText(R.id.subtitle, subtitle);
        }
        notificationManager.notify(notificationId, notificationCompatBuilder.build());
    }

    private void updateNotificationTheme(String titleColor, String subtitleColor, String backgroundColor) {
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "update notification theme with titleColor[" + titleColor + "], subtitleColor[" + subtitleColor + "], backgroundColor[" + backgroundColor + "]");
        }
        if (titleColor != null) {
            remoteView.setTextColor(R.id.title, Color.parseColor(titleColor));
        }
        if (subtitleColor != null) {
            remoteView.setTextColor(R.id.subtitle, Color.parseColor(subtitleColor));
        }
        if (backgroundColor != null) {
            remoteView.setInt(R.id.container, "setBackgroundColor", Color.parseColor(backgroundColor));
        }
        notificationManager.notify(notificationId, notificationCompatBuilder.build());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.hasExtra(POSITION_NOTIFY_INTERVAL_KEY)) {
            this.positionNotifyInterval = intent.getLongExtra(POSITION_NOTIFY_INTERVAL_KEY, 200);
        }
        if (intent.hasExtra(AUDIO_FOCUS_KEY)) {
            this.audioFocus = intent.getBooleanExtra(AUDIO_FOCUS_KEY, true);
        }
        if (intent.hasExtra(ENABLE_LOGGING_KEY)) {
            this.enableLogging = intent.getBooleanExtra(ENABLE_LOGGING_KEY, false);
        }
        if (audioManager == null) {
            IntentFilter intentFilter = new IntentFilter(BROADCAST_TO_SERVICE);
            registerReceiver(playerReceiver, intentFilter);
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioFocus) {
                onAudioFocusChangeListener = focusChange -> {
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (status == PLAYER_STATUS_PAUSED && autoResume) {
                                doResumePlayer();
                                autoResume = false;
                            }
                            if (enableLogging) {
                                Log.i(LOGGING_LABEL, "AUDIOFOCUS_GAIN");
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                            if (enableLogging) {
                                Log.i(LOGGING_LABEL, "AUDIOFOCUS_GAIN_TRANSIENT");
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                            if (enableLogging) {
                                Log.i(LOGGING_LABEL, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS:
                            if (enableLogging) {
                                Log.i(LOGGING_LABEL, "AUDIOFOCUS_LOSS");
                            }
                            if (status == PLAYER_STATUS_PLAYING) {
                                pausePlayer();
                                autoResume = true;
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            if (enableLogging) {
                                Log.i(LOGGING_LABEL, "AUDIOFOCUS_LOSS_TRANSIENT");
                            }
                            if (status == PLAYER_STATUS_PLAYING) {
                                pausePlayer();
                                autoResume = true;
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            if (enableLogging) {
                                Log.i(LOGGING_LABEL, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                            }
                            if (status == PLAYER_STATUS_PLAYING) {
                                pausePlayer();
                                autoResume = true;
                            }
                            break;
                    }
                };
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                }
            }
        }
        if (player != null && player.getPlayWhenReady()) {
            sendPlayerStatus(PLAYER_STATUS_PLAYING, null);
        }
        createNotification();
        startForeground(notificationId, notificationCompatBuilder.build());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(playerReceiver);
        releasePlayer();
    }

    private BroadcastReceiver playerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BROADCAST_TO_SERVICE.equalsIgnoreCase(action)) {
                int function = intent.getIntExtra(PLAYER_FUNCTION_TYPE, 0);
                switch (function) {
                    case CHANGE_PLAYER_TRACK:
                        changeTrack(intent.getStringExtra(PLAYER_TRACK_URL), intent.getStringExtra(PLAYER_HEADERS));
                        break;
                    case STOP_MEDIA_PLAYER:
                        stopPlayer();
                        break;
                    case PLAY_MEDIA_PLAYER:
                        setVolume(intent.getFloatExtra(PLAYER_VOLUME, -1));
                        seekTo(intent.getIntExtra(PLAYER_POSITION, -1));
                        startMediaPlayer(intent.getStringExtra(PLAYER_TRACK_URL), intent.getStringExtra(PLAYER_HEADERS));
                        break;
                    case PAUSE_MEDIA_PLAYER:
                        pausePlayer();
                        break;
                    case RESUME_MEDIA_PLAYER:
                        resumePlayer();
                        break;
                    case TOGGLE_MEDIA_PLAYER:
                        togglePlayer();
                        break;
                    case SET_VOLUME:
                        setVolume(intent.getFloatExtra(PLAYER_VOLUME, -1));
                        break;
                    case SEEK_TO:
                        seekTo(intent.getIntExtra(PLAYER_POSITION, -1));
                        break;
                    case UPDATE_NOTIFICATION:
                        updateNotification(intent.getStringExtra(NOTIFICATION_TITLE_KEY), intent.getStringExtra(NOTIFICATION_SUBTITLE_KEY));
                        break;
                    case UPDATE_NOTIFICATION_THEME:
                        updateNotificationTheme(intent.getStringExtra(NOTIFICATION_TITLE_COLOR_KEY), intent.getStringExtra(NOTIFICATION_SUBTITLE_COLOR_KEY), intent.getStringExtra(NOTIFICATION_BACKGROUND_COLOR_KEY));
                        break;
                    case UPDATE_OPTIONS:
                        if (intent.hasExtra(POSITION_NOTIFY_INTERVAL_KEY)) {
                            MediaPlayerService.this.positionNotifyInterval = intent.getLongExtra(POSITION_NOTIFY_INTERVAL_KEY, 200);
                        }
                        if (intent.hasExtra(AUDIO_FOCUS_KEY)) {
                            MediaPlayerService.this.audioFocus = intent.getBooleanExtra(AUDIO_FOCUS_KEY, true);
                        }
                        break;
                }

            }
        }
    };

    private void togglePlayer() {
        Log.i(LOGGING_LABEL, "toggle player position:" + position + " player null: " + (player == null) + " " + (player != null && player.getPlayWhenReady()));
        if (player == null && position == C.POSITION_UNSET) {
            startMediaPlayer(null, null);
            return;
        }
        if (player != null && player.getPlayWhenReady()) {
            pausePlayer();
        } else {
            resumePlayer();
        }
        autoResume = false;
    }

    private void pausePlayer() {
        releasePlayer();
        sendPlayerStatus(PLAYER_STATUS_PAUSED, null);
        Log.i(LOGGING_LABEL, "player is null after pause? " + (player == null));
    }

    private void resumePlayer() {
        autoResume = false;
        if (audioFocus) {
            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result = audioManager.requestAudioFocus(new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(onAudioFocusChangeListener)
                        .build());
            } else {
                result = audioManager.requestAudioFocus(onAudioFocusChangeListener,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN);
            }
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                doResumePlayer();
            } else {
                Log.w(LOGGING_LABEL, "requestAudioFocus failed " + result);
            }
        } else {
            doResumePlayer();
        }
    }

    private void doResumePlayer() {
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "resume player");
        }
        initializePlayer();
        sendPlayerStatus(PLAYER_STATUS_PLAYING, null);
    }

    private void changeTrack(String url, String headers) {
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "change url to " + url);
        }
        _stopPlayer();
        startMediaPlayer(url, headers);
    }

    private void stopPlayer() {
        if (player != null) {
            if (enableLogging) {
                Log.i(LOGGING_LABEL, "stop player");
            }
            _stopPlayer();
            sendPlayerStatus(PLAYER_STATUS_STOPPED, null);
        }
    }

    private void _stopPlayer() {
        releasePlayer();
        position = C.POSITION_UNSET;
    }

    private void setVolume(float volume) {
        if (volume == -1) {
            return;
        }
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "set volume to " + volume);
        }
        volume = Math.max(0, Math.min(1, volume));
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (volume * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
        } catch (Exception ignore) {
        }
    }

    private void seekTo(int position) {
        if (position >= 0) {
            if (enableLogging) {
                Log.i(LOGGING_LABEL, "seek to " + position);
            }
            if (player != null) {
                player.seekTo(position);
                this.position = C.POSITION_UNSET;
            } else {
                this.position = position;
            }
        }
    }

    private void initializePlayer() {
        bufferedPercent = 0;
        if (player == null) {
            player = ExoPlayerFactory.newSimpleInstance(this, new DefaultRenderersFactory(this), new DefaultTrackSelector());
            player.addListener(new PlayerEventListener());
            player.setPlayWhenReady(startAutoPlay);
        }
        boolean resetPosition = position == C.POSITION_UNSET;
        if (position != C.POSITION_UNSET) {
            player.seekTo(position);
            position = C.POSITION_UNSET;
        }
        String userAgent = this.headers != null && this.headers.containsKey("User-Agent") ? this.headers.get("User-Agent") : DEFAULT_USER_AGENT;
        Uri uri = Uri.parse(this.url);
        DataSource.Factory dataSourceFactory;
        if("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
            dataSourceFactory = new DefaultHttpDataSourceFactory(userAgent);
            if(headers != null) {
                ((DefaultHttpDataSourceFactory)dataSourceFactory).getDefaultRequestProperties().set(headers);
            }
        } else {
            dataSourceFactory = new DefaultDataSourceFactory(this, userAgent);
        }
        ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                .setExtractorsFactory(new DefaultExtractorsFactory())
                .createMediaSource(uri);
        player.prepare(mediaSource, resetPosition, false);
        startPositionUpdate();
    }

    private void releasePlayer() {
        if(player != null) {
            updateStartPosition();
            stopPositionUpdate();
            player.setPlayWhenReady(false);
            player.stop();
            player.release();
            player = null;
        }
    }

    private boolean startAutoPlay = true;

    private void updateStartPosition() {
        if (player != null) {
            startAutoPlay = player.getPlayWhenReady();
            position = Math.max(0, player.getContentPosition());
        }
    }

    public void startMediaPlayer(String url, String headers) {
        durationSent = false;
        autoResume = false;
        startAutoPlay = true;
        if (!TextUtils.isEmpty(url)) {
            this.url = url;
            this.headers = parseHeaders(headers);
        }
        if (this.url == null) {
            return;
        }
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "start media player with url " + this.url);
        }

        try {
            initializePlayer();
            sendPlayerStatus(PLAYER_STATUS_PLAYING, null);
        } catch (Exception e) {
            Log.e("MediaPlayerService", "failed to start mediaPlayer", e);
        }
    }

    private void sendPlayerStatus(int status, String message) {
        this.status = status;
        remoteView.setImageViewResource(R.id.play_btn, status == PLAYER_STATUS_PLAYING ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        notificationManager.notify(notificationId, notificationCompatBuilder.build());
        Intent intent = new Intent();
        intent.setAction(SERVICE_TO_BROADCAST);
        intent.putExtra(ACTION_TYPE_KEY, ACTION_TYPE_STATUS);
        intent.putExtra(PLAYER_STATUS_KEY, status);
        intent.putExtra(PLAYER_STATUS_MESSAGE_KEY, message);
        sendBroadcast(intent);
    }

    private void sendPlayerDuration() {
        if(durationSent || player == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setAction(SERVICE_TO_BROADCAST);
        intent.putExtra(ACTION_TYPE_KEY, ACTION_TYPE_DURATION);
        intent.putExtra(PLAYER_DURATION_KEY, player.getDuration());
        sendBroadcast(intent);
        durationSent = true;
    }

    private void startPositionUpdate() {
        handler.post(this);
    }

    private void stopPositionUpdate() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void run() {
        if (player != null && player.getPlayWhenReady()) {
            Intent intent = new Intent();
            intent.setAction(SERVICE_TO_BROADCAST);
            intent.putExtra(ACTION_TYPE_KEY, ACTION_TYPE_POSITION);
            intent.putExtra(PLAYER_POSITION_KEY, player.getCurrentPosition());
            sendBroadcast(intent);
            if(player.getBufferedPercentage() != bufferedPercent) {
                bufferedPercent = player.getBufferedPercentage();
                Intent bufferingIntent = new Intent();
                bufferingIntent.setAction(SERVICE_TO_BROADCAST);
                bufferingIntent.putExtra(ACTION_TYPE_KEY, ACTION_TYPE_BUFFER);
                bufferingIntent.putExtra(PLAYER_BUFFER_KEY, bufferedPercent);
                sendBroadcast(bufferingIntent);
            }
            handler.postDelayed(this, positionNotifyInterval);
        } else {
            stopPositionUpdate();
        }
    }

    private static Class getMainActivityClass(Context context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        //noinspection ConstantConditions
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            Log.e(LOGGING_LABEL, "failed to get MainActivity", e);
            return null;
        }
    }

    private static Map<String, String> parseHeaders(String raw) {
        Map<String, String> result = new HashMap<>();
        if(TextUtils.isEmpty(raw)) {
            return result;
        }
        try {
            JSONObject json = new JSONObject(raw);
            Iterator<String> keys = json.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                String value = json.getString(key);
                if(TextUtils.isEmpty(value)) {
                    continue;
                }
                result.put(key, value);
            }
        } catch (JSONException e) {
            Log.e(LOGGING_LABEL, "failed to parse headers", e);
        }
        return result;
    }

    private class PlayerEventListener implements Player.EventListener {
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            Log.e(LOGGING_LABEL, "error occurred while playing", error);
            _stopPlayer();
            sendPlayerStatus(PLAYER_STATUS_ERROR, error.getMessage());
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch(playbackState) {
                case Player.STATE_IDLE:
                    if(enableLogging) {
                        Log.i(LOGGING_LABEL, "player state idle");
                    }
                    break;
                case Player.STATE_ENDED:
                    if(enableLogging) {
                        Log.i(LOGGING_LABEL, "player state end");
                    }
                    sendPlayerStatus(PLAYER_STATUS_COMPLETED, null);
                    _stopPlayer();
                    break;
                case Player.STATE_READY:
                    if(enableLogging) {
                        Log.i(LOGGING_LABEL, "player state ready");
                    }
                    sendPlayerDuration();
                    break;
                case Player.STATE_BUFFERING:
                    if(enableLogging) {
                        Log.i(LOGGING_LABEL, "player state buffering");
                    }
                    break;
            }
        }
    }
}
