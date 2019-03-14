import 'dart:async';

import 'package:flutter/services.dart';

typedef void TimeChangeHandler(int value);
typedef void ErrorHandler(String message);
typedef void AudioPlayerStateChangeHandler(AudioPlayerState state);

enum AudioPlayerState {
  STOPPED,
  PLAYING,
  PAUSED,
  COMPLETED,
}

/// This represents a single AudioPlayer, that can play one audio at a time (per instance).
///
/// It features methods to play, loop, pause, stop, seek the audio, and some useful hooks for handlers and callbacks.
class AudioPlayer {
  MethodChannel _channel;

  /// This enables more verbose logging, if desired.
  static bool logEnabled = false;

  AudioPlayerState _audioPlayerState = null;

  AudioPlayerState get state => _audioPlayerState;

  void set state(AudioPlayerState state) {
    if (audioPlayerStateChangeHandler != null) {
      audioPlayerStateChangeHandler(state);
    }
    _audioPlayerState = state;
  }

  /// This handler returns the duration of the file, when it's available (it might take a while because it's being downloaded or buffered).
  TimeChangeHandler durationHandler;

  /// This handler updates the current position of the audio. You can use it to make a progress bar, for instance.
  TimeChangeHandler positionHandler;

  /// This handler updates the current buffer percent of the audio. You can use it to make a progress bar, for instance.
  TimeChangeHandler bufferHandler;

  AudioPlayerStateChangeHandler audioPlayerStateChangeHandler;

  /// This is called when an unexpected error is thrown in the native code.
  ErrorHandler errorHandler;

  /// Creates a new instance and assigns it with a new random unique id.
  AudioPlayer() {
    _channel =
    const MethodChannel('com.whaleread/audio_player_with_notification')
      ..setMethodCallHandler(_platformCallHandler);
  }

  Future<int> _invokeMethod(String method,
      [Map<String, dynamic> arguments = const {}]) {
    return _channel
        .invokeMethod(method, arguments)
        .then((result) => (result as int));
  }

  Future<int> init({bool audioFocus, int positionNotifyInterval, String notificationName}) async {
    return await _invokeMethod('init', {'audioFocus': audioFocus, 'positionNotifyInterval': positionNotifyInterval, 'enableLogging': logEnabled, 'notificationName': notificationName});
  }

  Future<int> dispose() async {
    return await _invokeMethod('dispose');
  }

  /// Play audio. Url can be a remote url (isLocal = false) or a local file system path (isLocal = true).
  Future<int> play(String url,
      {bool isLocal: false, double volume: -1, int position: 0, String headers}) async {
    int result = await _invokeMethod(
        'play', {'url': url, 'isLocal': isLocal, 'volume': volume, 'position': position, 'headers': headers});
    return result;
  }

  /// Pause the currently playing audio (resumes from this point).
  Future<int> pause() async {
    int result = await _invokeMethod('pause');
    return result;
  }

  /// Stop the currently playing audio (resumes from the beginning).
  Future<int> stop() async {
    int result = await _invokeMethod('stop');
    return result;
  }

  /// Resumes the currently paused or stopped audio (like calling play but without changing the parameters).
  Future<int> resume() async {
    int result = await _invokeMethod('resume');
    return result;
  }

  /// Move the cursor to the desired position.
  Future<int> seek(int position) {
    return _invokeMethod('seek', {'position': position});
  }

  /// Sets the volume (ampliutde). 0.0 is mute and 1.0 is max, the rest is linear interpolation.
  Future<int> setVolume(double volume) {
    return _invokeMethod('setVolume', {'volume': volume});
  }

  Future<int> updateNotification({String title, String subtitle}) {
    return _invokeMethod(
        'updateNotification', {'title': title, 'subtitle': subtitle});
  }

  Future<int> updateNotificationTheme({String titleColor, String subtitleColor, String backgroundColor}) {
    return _invokeMethod(
        'updateNotificationTheme', {'titleColor': titleColor, 'subtitleColor': subtitleColor, 'backgroundColor': backgroundColor});
  }

  /// Changes the url (source), without resuming playback (like play would do).
  ///
  /// This will keep the resource prepared (on Android) for when resume is called.
  Future<int> setUrl(String url, {bool isLocal: false, String headers}) {
    return _invokeMethod('setUrl', {'url': url, 'isLocal': isLocal, 'headers': headers});
  }

  static void _log(String param) {
    if (logEnabled) {
      print(param);
    }
  }

  Future<void> _platformCallHandler(MethodCall call) async {
//    _log('_platformCallHandler call ${call.method} ${call.arguments}');
    dynamic value = call.arguments;
    switch (call.method) {
      case 'onPlay':
        state = AudioPlayerState.PLAYING;
        break;
      case 'onPause':
        state = AudioPlayerState.PAUSED;
        break;
      case 'onStop':
        state = AudioPlayerState.STOPPED;
        break;
      case 'onDuration':
        if (durationHandler != null) {
          durationHandler(value);
        }
        break;
      case 'onPosition':
        if (positionHandler != null) {
          positionHandler(value);
        }
        break;
      case 'onBuffer':
        if (bufferHandler != null) {
          bufferHandler(value);
        }
        break;
      case 'onComplete':
        state = AudioPlayerState.COMPLETED;
        break;
      case 'onError':
        state = AudioPlayerState.STOPPED;
        if (errorHandler != null) {
          errorHandler(value);
        }
        break;
      default:
        _log('Unknowm method ${call.method} ');
    }
  }
}
