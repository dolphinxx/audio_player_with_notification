package com.whaleread.audio_player_with_notification;

public interface MediaPlayerListener {
    void onPlay();

    void onPause();

    void onStop();

    void onComplete();

    void onError(String message);

    void onDuration(long duration);

    void onPosition(long position);

    void onBuffer(int percent);
}
