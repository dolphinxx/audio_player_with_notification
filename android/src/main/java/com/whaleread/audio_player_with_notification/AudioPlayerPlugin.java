package com.whaleread.audio_player_with_notification;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class AudioPlayerPlugin implements MethodCallHandler {

    private static final Logger LOGGER = Logger.getLogger(AudioPlayerPlugin.class.getCanonicalName());

    private static final String ID = "com.whaleread/audio_player_with_notification";

    private final MethodChannel channel;

    private MediaPlayerDelegate player;

    public static void registerWith(final Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), ID);
        channel.setMethodCallHandler(new AudioPlayerPlugin(registrar, channel));
    }

    private AudioPlayerPlugin(final Registrar registrar, final MethodChannel channel) {
        this.channel = channel;
        this.channel.setMethodCallHandler(this);
        this.player = new MediaPlayerDelegate(registrar.context().getApplicationContext());
        this.player.setListener(new MediaPlayerListener() {
            @Override
            public void onPlay() {
                channel.invokeMethod("onPlay", null);
            }

            @Override
            public void onPause() {
                channel.invokeMethod("onPause", null);
            }

            @Override
            public void onStop() {
                channel.invokeMethod("onStop", null);
            }

            @Override
            public void onComplete() {
                channel.invokeMethod("onComplete", null);
            }

            @Override
            public void onError() {
                channel.invokeMethod("onError", null);
            }

            @Override
            public void onDuration(int duration) {
                channel.invokeMethod("onDuration", duration);
            }

            @Override
            public void onPosition(int position) {
                channel.invokeMethod("onPosition", position);
            }

            @Override
            public void onBuffer(int percent) {
                channel.invokeMethod("onBuffer", percent);
            }
        });
    }

    @Override
    public void onMethodCall(final MethodCall call, final MethodChannel.Result response) {
        try {
            handleMethodCall(call, response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error!", e);
            response.error("Unexpected error!", e.getMessage(), e);
        }
    }

    private void handleMethodCall(final MethodCall call, final MethodChannel.Result response) {
        switch (call.method) {
            case "init": {
                Boolean audioFocus = call.argument("audioFocus");
                Integer positionNotifyInterval = call.argument("positionNotifyInterval");
                Boolean enableLogging = call.argument("enableLogging");
                player.createPlayer(audioFocus, positionNotifyInterval, enableLogging);
                break;
            }
            case "dispose": {
                player.destroyPlayer();
                break;
            }
            case "play": {
                final String url = call.argument("url");
                Double volume = call.argument("volume");
                Integer position = call.argument("position");
                String headers = call.argument("headers");
                player.play(url, volume == null ? -1 : volume.floatValue(), position == null ? -1 : position, headers);
                break;
            }
            case "resume": {
                player.resume();
                break;
            }
            case "pause": {
                player.pause();
                break;
            }
            case "stop": {
                player.stop();
                break;
            }
            case "seek": {
                final int position = call.argument("position");
                player.seekTo(position);
                break;
            }
            case "setVolume": {
                final double volume = call.argument("volume");
                player.setVolume((float) volume);
                break;
            }
            case "setUrl": {
                final String url = call.argument("url");
                String headers = call.argument("headers");
                player.setUrl(url, headers);
                break;
            }
            case "updateNotification": {
                String title = call.argument("title");
                String subtitle = call.argument("subtitle");
                this.player.updateNotification(title, subtitle);
                break;
            }
            case "updateNotificationTheme": {
                String titleColor = call.argument("titleColor");
                String subtitleColor = call.argument("subtitleColor");
                String backgroundColor = call.argument("backgroundColor");
                this.player.updateNotificationTheme(titleColor, subtitleColor, backgroundColor);
                break;
            }
            default: {
                response.notImplemented();
                return;
            }
        }
        response.success(1);
    }
}
