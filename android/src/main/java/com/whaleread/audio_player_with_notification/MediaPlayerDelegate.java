package com.whaleread.audio_player_with_notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.Nullable;
import android.util.Log;

public class MediaPlayerDelegate {
    private Context context;
    private MediaPlayerListener listener;
    private boolean initialized = false;
    private boolean enableLogging = false;
    private static final String LOGGING_LABEL = "AudioPlayer";

    private int currentPlayerStatus = MediaPlayerService.PLAYER_STATUS_INITIAL;

    /**
     * @param context {@link Context}
     */
    public MediaPlayerDelegate(Context context) {
        this.context = context;
    }

    public void createPlayer(Boolean audioFocus, Integer positionNotifyInterval, Boolean enableLogging) {
        if(initialized) {
            return;
        }
        Intent intent = new Intent(context, MediaPlayerService.class);
        if(audioFocus != null) {
            intent.putExtra(MediaPlayerService.AUDIO_FOCUS_KEY, audioFocus);
        }
        if(positionNotifyInterval != null) {
            intent.putExtra(MediaPlayerService.POSITION_NOTIFY_INTERVAL_KEY, positionNotifyInterval);
        }
        if(enableLogging != null) {
            this.enableLogging = enableLogging;
            intent.putExtra(MediaPlayerService.ENABLE_LOGGING_KEY, enableLogging);
        }
        if(this.enableLogging) {
            Log.i(LOGGING_LABEL, "createPlayer");
        }
        context.startService(intent);
        context.registerReceiver(receiverFromService, new IntentFilter(MediaPlayerService.SERVICE_TO_BROADCAST));
        initialized = true;
    }

    public void destroyPlayer() {
        if(initialized) {
            context.unregisterReceiver(receiverFromService);
            context.stopService(new Intent(context, MediaPlayerService.class));
            this.initialized = false;
            this.currentPlayerStatus = MediaPlayerService.PLAYER_STATUS_INITIAL;
            if(this.enableLogging) {
                Log.i(LOGGING_LABEL, "destroyPlayer");
            }
        }
    }

    public void setListener(MediaPlayerListener listener) {
        this.listener = listener;
    }

    public void play(@Nullable String url, float volume, int position) {
        if(!initialized) {
            createPlayer(null, null, null);
        }
        if (currentPlayerStatus != MediaPlayerService.PLAYER_STATUS_PLAYING && currentPlayerStatus != MediaPlayerService.PLAYER_STATUS_PAUSED) {
            startMediaPlayer(url, volume, position);
            return;
        }
        if (currentPlayerStatus == MediaPlayerService.PLAYER_STATUS_PLAYING) {
            Intent intent = new Intent();
            intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
            intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.PAUSE_MEDIA_PLAYER);
            context.sendBroadcast(intent);
        } else {
            Intent intent = new Intent();
            intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
            intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.RESUME_MEDIA_PLAYER);
            context.sendBroadcast(intent);
        }
    }

    public void pause() {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.PAUSE_MEDIA_PLAYER);
        context.sendBroadcast(intent);
    }

    public void resume() {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.RESUME_MEDIA_PLAYER);
        context.sendBroadcast(intent);
    }

    public void stop() {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.STOP_MEDIA_PLAYER);
        context.sendBroadcast(intent);
    }

    public void setVolume(float volume) {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.SET_VOLUME);
        intent.putExtra(MediaPlayerService.PLAYER_VOLUME, volume);
        context.sendBroadcast(intent);
    }

    public void seekTo(int position) {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.SEEK_TO);
        intent.putExtra(MediaPlayerService.PLAYER_POSITION, position);
        context.sendBroadcast(intent);
    }

    public void setUrl(String url) {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.CHANGE_PLAYER_TRACK);
        intent.putExtra(MediaPlayerService.PLAYER_TRACK_URL, url);
        context.sendBroadcast(intent);
    }

    public void updateNotification(String title, String subtitle) {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.UPDATE_NOTIFICATION);
        intent.putExtra(MediaPlayerService.NOTIFICATION_TITLE_KEY, title);
        intent.putExtra(MediaPlayerService.NOTIFICATION_SUBTITLE_KEY, subtitle);
        context.sendBroadcast(intent);
    }

    /**
     *
     * @param titleColor notification title color
     * @param subtitleColor notification subtitle color
     * @param backgroundColor notification background color
     */
    public void updateNotificationTheme(String titleColor, String subtitleColor, String backgroundColor) {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.UPDATE_NOTIFICATION_THEME);
        intent.putExtra(MediaPlayerService.NOTIFICATION_TITLE_COLOR_KEY, titleColor);
        intent.putExtra(MediaPlayerService.NOTIFICATION_SUBTITLE_COLOR_KEY, subtitleColor);
        intent.putExtra(MediaPlayerService.NOTIFICATION_BACKGROUND_COLOR_KEY, backgroundColor);
        context.sendBroadcast(intent);
    }

    /**
     *
     * @param audioFocus request audio focus before play, default true
     * @param positionNotifyInterval player position notify interval, default 200
     */
    public void updateOptions(Boolean audioFocus, Integer positionNotifyInterval) {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.UPDATE_OPTIONS);
        if(audioFocus != null) {
            intent.putExtra(MediaPlayerService.AUDIO_FOCUS_KEY, audioFocus);
        }
        if(positionNotifyInterval != null) {
            intent.putExtra(MediaPlayerService.POSITION_NOTIFY_INTERVAL_KEY, positionNotifyInterval);
        }
        context.sendBroadcast(intent);
    }

    public int getState() {
        return this.currentPlayerStatus;
    }

    public void startMediaPlayer(String url, float volume, int position) {
        Intent intent = new Intent();
        intent.setAction(MediaPlayerService.BROADCAST_TO_SERVICE);
        intent.putExtra(MediaPlayerService.PLAYER_FUNCTION_TYPE, MediaPlayerService.PLAY_MEDIA_PLAYER);
        intent.putExtra(MediaPlayerService.PLAYER_TRACK_URL, url);
        intent.putExtra(MediaPlayerService.PLAYER_VOLUME, volume);
        intent.putExtra(MediaPlayerService.PLAYER_POSITION, position);
        context.sendBroadcast(intent);
    }

    private void onServicePlay() {
        if (listener != null) {
            listener.onPlay();
        }
    }

    private void onServicePause() {
        if (listener != null) {
            listener.onPause();
        }
    }

    private void onServiceStop() {
        if (listener != null) {
            listener.onStop();
        }
    }

    private void onServiceComplete() {
        if (listener != null) {
            listener.onComplete();
        }
    }

    private void onServiceError() {
        if (listener != null) {
            listener.onError();
        }
    }

    private void onServiceDuration(int duration) {
        if (listener != null) {
            listener.onDuration(duration);
        }
    }

    private void onServicePosition(int position) {
        if (listener != null) {
            listener.onPosition(position);
        }
    }

    private void onServiceBuffer(int percent) {
        if (listener != null) {
            listener.onBuffer(percent);
        }
    }

    private BroadcastReceiver receiverFromService = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MediaPlayerService.SERVICE_TO_BROADCAST.equals(action)) {
                int actionType = intent.getIntExtra(MediaPlayerService.ACTION_TYPE_KEY, 0);
                if (actionType == MediaPlayerService.ACTION_TYPE_STATUS) {
                    currentPlayerStatus = intent.getIntExtra(MediaPlayerService.PLAYER_STATUS_KEY, 0);
                    switch (currentPlayerStatus) {
                        case MediaPlayerService.PLAYER_STATUS_PLAYING:
                            onServicePlay();
                            break;
                        case MediaPlayerService.PLAYER_STATUS_PAUSED:
                            onServicePause();
                            break;
                        case MediaPlayerService.PLAYER_STATUS_STOPPED:
                            onServiceStop();
                            break;
                        case MediaPlayerService.PLAYER_STATUS_COMPLETED:
                            onServiceComplete();
                            break;
                        case MediaPlayerService.PLAYER_STATUS_ERROR:
                            onServiceError();
                            break;
                    }
                } else if (actionType == MediaPlayerService.ACTION_TYPE_DURATION) {
                    onServiceDuration(intent.getIntExtra(MediaPlayerService.PLAYER_DURATION_KEY, 0));
                } else if (actionType == MediaPlayerService.ACTION_TYPE_POSITION) {
                    onServicePosition(intent.getIntExtra(MediaPlayerService.PLAYER_POSITION_KEY, 0));
                } else if (actionType == MediaPlayerService.ACTION_TYPE_BUFFER) {
                    onServiceBuffer(intent.getIntExtra(MediaPlayerService.PLAYER_BUFFER_KEY, 0));
                }
            }
        }
    };
}
