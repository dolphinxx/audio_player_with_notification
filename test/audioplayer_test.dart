import 'package:flutter/services.dart';
import 'package:test/test.dart';
import 'package:audio_player_with_notification/audio_player.dart';

void main() {
  List<MethodCall> calls = [];
  const channel = const MethodChannel('com.whaleread/audio_player_with_notification');
  channel.setMockMethodCallHandler((MethodCall call) {
    calls.add(call);
  });

  group('AudioPlayer', () {
    test('#play', () async {
      calls.clear();
      AudioPlayer player = new AudioPlayer();
      await player.play('internet.com/file.mp3');
      expect(calls, hasLength(1));
      expect(calls[0].method, 'play');
      expect(calls[0].arguments['url'], 'internet.com/file.mp3');
    });
    test('updateNotification', () async {
      String title = '一生所爱';
      String subtitle = '电影《大话西游》插曲';
      calls.clear();
      AudioPlayer player = new AudioPlayer();
      await player.updateNotification(title: title, subtitle: subtitle);
      expect(calls, hasLength(1));
      expect(calls[0].method, 'updateNotification');
      expect(calls[0].arguments, allOf(
          containsPair('title', title),
          containsPair('subtitle', subtitle),
      ));
    });
  });
}
