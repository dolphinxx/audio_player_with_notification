package com.whaleread.audio_player_with_notification;

public interface MediaPlayerListener {
    void onPlay();

    void onPause();

    void onStop();

    void onComplete();

    void onError();

    void onDuration(int duration);

    void onPosition(int position);

    void onBuffer(int percent);
}
