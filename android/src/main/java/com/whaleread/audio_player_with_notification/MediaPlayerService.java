package com.whaleread.audio_player_with_notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

public class MediaPlayerService extends Service implements Runnable {
    @SuppressWarnings("ConstantConditions")
    public static final String BROADCAST_TO_SERVICE = MediaPlayerService.class.getPackage().getName() + ".broadcastToService";
    @SuppressWarnings("ConstantConditions")
    public static final String SERVICE_TO_BROADCAST = MediaPlayerService.class.getPackage().getName() + ".serviceToBroadcast";
    public static final String POSITION_NOTIFY_INTERVAL_KEY = "positionNotifyInterval";
    public static final String AUDIO_FOCUS_KEY = "audioFocus";
    public static final String ACTION_TYPE_KEY = "actionType";
    public static final int ACTION_TYPE_STATUS = 1;
    public static final int ACTION_TYPE_DURATION = 2;
    public static final int ACTION_TYPE_POSITION = 3;
    public static final String PLAYER_FUNCTION_TYPE = "playerFunctionType";
    public static final String PLAYER_TRACK_URL = "trackURL";
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
    public static final String PLAYER_DURATION_KEY = "playerDuration";
    public static final String PLAYER_POSITION_KEY = "playerCurrentPosition";
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

    private volatile MediaPlayer player;
    private AudioManager audioManager;
    private Handler handler = new Handler();
    private String url;
    private int position;
    private float volume = -1;
    private boolean prepared = false;
    private long positionNotifyInterval = 200;
    private int status = PLAYER_STATUS_INITIAL;
    private boolean audioFocus = true;
    private boolean enableLogging = true;
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


        String channelId = "com.whaleread/audio_player_with_notification";
        notificationCompatBuilder =
                new NotificationCompat.Builder(this.getApplicationContext(), channelId);

        notificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationCompatBuilder
                // Title for API < 16 devices.
                .setCustomContentView(remoteView)
                .setContent(remoteView)
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

    private void updateNotificationTheme(int titleColor, int subtitleColor, int backgroundColor) {
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "update notification theme with titleColor[" + titleColor + "], subtitleColor[" + subtitleColor + "], backgroundColor[" + backgroundColor + "]");
        }
        if (titleColor != -1) {
            remoteView.setTextColor(R.id.title, titleColor);
        }
        if (subtitleColor != -1) {
            remoteView.setTextColor(R.id.subtitle, subtitleColor);
        }
        if (backgroundColor != -1) {
            remoteView.setInt(R.id.container, "setBackgroundColor", backgroundColor);
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
        if (audioManager == null) {
            IntentFilter intentFilter = new IntentFilter(BROADCAST_TO_SERVICE);
            registerReceiver(playerReceiver, intentFilter);
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioFocus) {
                onAudioFocusChangeListener = focusChange -> {
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (status == PLAYER_STATUS_PAUSED) {
                                doResumePlayer();
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
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            if (enableLogging) {
                                Log.i(LOGGING_LABEL, "AUDIOFOCUS_LOSS_TRANSIENT");
                            }
                            if (status == PLAYER_STATUS_PLAYING) {
                                pausePlayer();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            if (enableLogging) {
                                Log.i(LOGGING_LABEL, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                            }
                            if (status == PLAYER_STATUS_PLAYING) {
                                pausePlayer();
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
        if (player != null && player.isPlaying()) {
            sendPlayerStatus(PLAYER_STATUS_PLAYING);
        }
        createNotification();
        startForeground(notificationId, notificationCompatBuilder.build());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(playerReceiver);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private BroadcastReceiver playerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BROADCAST_TO_SERVICE.equalsIgnoreCase(action)) {
                int function = intent.getIntExtra(PLAYER_FUNCTION_TYPE, 0);
                switch (function) {
                    case CHANGE_PLAYER_TRACK:
                        changeTrack(intent.getStringExtra(PLAYER_TRACK_URL));
                        break;
                    case STOP_MEDIA_PLAYER:
                        stopPlayer();
                        break;
                    case PLAY_MEDIA_PLAYER:
                        setVolume(intent.getFloatExtra(PLAYER_VOLUME, -1));
                        seekTo(intent.getIntExtra(PLAYER_POSITION, -1));
                        startMediaPlayer(intent.getStringExtra(PLAYER_TRACK_URL));
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
                        updateNotificationTheme(intent.getIntExtra(NOTIFICATION_TITLE_COLOR_KEY, -1), intent.getIntExtra(NOTIFICATION_SUBTITLE_COLOR_KEY, -1), intent.getIntExtra(NOTIFICATION_BACKGROUND_COLOR_KEY, -1));
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
        if (player == null) {
            startMediaPlayer(null);
            return;
        }
        if (player.isPlaying()) {
            pausePlayer();
        } else {
            resumePlayer();
        }
    }

    private void pausePlayer() {
        if (player != null && player.isPlaying()) {
            player.pause();
            sendPlayerStatus(PLAYER_STATUS_PAUSED);
            stopPositionUpdate();
        }
    }

    private void resumePlayer() {
        if (player != null && !player.isPlaying()) {
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
                }
            } else {
                doResumePlayer();
            }
        }
    }

    private void doResumePlayer() {
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "resume player");
        }
        player.start();
        sendPlayerStatus(PLAYER_STATUS_PLAYING);
        startPositionUpdate();
    }

    private void changeTrack(String url) {
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "change url to " + url);
        }
        _stopPlayer();
        startMediaPlayer(url);

    }

    private void stopPlayer() {
        if (player != null) {
            if (enableLogging) {
                Log.i(LOGGING_LABEL, "stop player");
            }
            _stopPlayer();
            sendPlayerStatus(PLAYER_STATUS_STOPPED);
        }
    }

    private void _stopPlayer() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
            stopPositionUpdate();
        }
    }

    private void setVolume(float volume) {
        if (volume == -1) {
            return;
        }
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "set volume to " + volume);
        }
        volume = Math.max(0, Math.min(1, volume));
        if (player != null) {
//                player.setVolume(volume, volume);
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (volume * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
            } catch (Exception ignore) {
            }
        } else {
            this.volume = volume;
        }
    }

    private void seekTo(int position) {
        if (position >= 0) {
            if (enableLogging) {
                Log.i(LOGGING_LABEL, "seek to " + position);
            }
            if (player != null && prepared) {
                player.seekTo(position);
            } else {
                this.position = position;
            }
        }
    }

    public void startMediaPlayer(String url) {
        if (!TextUtils.isEmpty(url)) {
            this.url = url;
        }
        if (this.url == null) {
            return;
        }
        if (enableLogging) {
            Log.i(LOGGING_LABEL, "start media player with url " + this.url);
        }
        if (player == null) {
            player = new MediaPlayer();
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(audioAttributes);
            } else {
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            if (volume != -1) {
//                player.setVolume(volume, volume);
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (volume * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
                } catch (Exception ignore) {
                }
                volume = -1;
            }
        }
        try {
            player.setDataSource(this.url);
            prepared = false;
            player.setOnErrorListener((mp, what, extra) -> {
                if (extra == MediaPlayer.MEDIA_ERROR_SERVER_DIED
                        || extra == MediaPlayer.MEDIA_ERROR_MALFORMED) {
                    sendPlayerStatus(PLAYER_STATUS_ERROR);
                } else if (extra == MediaPlayer.MEDIA_ERROR_IO) {
                    sendPlayerStatus(PLAYER_STATUS_ERROR);
                }
                _stopPlayer();
                return false;
            });
//            player.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
//
//                public void onBufferingUpdate(MediaPlayer mp, int percent) {
//                    Log.e("onBufferingUpdate", "" + percent);
//                }
//            });
            player.prepareAsync();
            player.setOnPreparedListener(mp -> {
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
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN);
                    }
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        player.start();
                        sendPlayerDuration(player.getDuration());
                        startPositionUpdate();
                        if (position != -1) {
                            player.seekTo(position);
                            position = -1;
                        }
                    }
                } else {
                    player.start();
                    sendPlayerDuration(player.getDuration());
                    startPositionUpdate();
                    if (position != -1) {
                        player.seekTo(position);
                        position = -1;
                    }
                }
                prepared = true;
            });
            player.setOnCompletionListener(mp -> {
                sendPlayerStatus(PLAYER_STATUS_COMPLETED);
                _stopPlayer();
            });
//            player.setOnInfoListener(new MediaPlayer.OnInfoListener() {
//                @Override
//                public boolean onInfo(MediaPlayer mp, int what, int extra) {
//                    return false;
//                }
//            });
            sendPlayerStatus(PLAYER_STATUS_PLAYING);
        } catch (Exception e) {
            Log.e("MediaPlayerService", "failed to start mediaPlayer", e);
        }
    }

    private void sendPlayerStatus(int status) {
        this.status = status;
        remoteView.setImageViewResource(R.id.play_btn, status == PLAYER_STATUS_PLAYING ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        notificationManager.notify(notificationId, notificationCompatBuilder.build());
        Intent intent = new Intent();
        intent.setAction(SERVICE_TO_BROADCAST);
        intent.putExtra(ACTION_TYPE_KEY, ACTION_TYPE_STATUS);
        intent.putExtra(PLAYER_STATUS_KEY, status);
        sendBroadcast(intent);
    }

    private void sendPlayerDuration(int duration) {
        Intent intent = new Intent();
        intent.setAction(SERVICE_TO_BROADCAST);
        intent.putExtra(ACTION_TYPE_KEY, ACTION_TYPE_DURATION);
        intent.putExtra(PLAYER_DURATION_KEY, duration);
        sendBroadcast(intent);
    }

    private void startPositionUpdate() {
        handler.post(this);
    }

    private void stopPositionUpdate() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void run() {
        if (player != null && player.isPlaying()) {
            Intent intent = new Intent();
            intent.setAction(SERVICE_TO_BROADCAST);
            intent.putExtra(ACTION_TYPE_KEY, ACTION_TYPE_POSITION);
            intent.putExtra(PLAYER_POSITION_KEY, player.getCurrentPosition());
            sendBroadcast(intent);
            handler.postDelayed(this, positionNotifyInterval);
        } else {
            stopPositionUpdate();
        }
    }
}
