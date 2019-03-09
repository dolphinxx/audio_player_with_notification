import 'dart:async';
import 'dart:io';
import 'dart:convert';
import 'package:convert/convert.dart';
import 'package:crypto/crypto.dart' as crypto;

import 'package:audio_player_with_notification/audio_player.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart';
import 'package:path_provider/path_provider.dart';

import 'player_slider.dart';

const songs = [
  {
    "name": "一生所爱",
    "album": "电影《大话西游》插曲",
    "url": "http://222.216.30.118:8088/201411542.mp3",
    "cache": true,
  }, {
    "name": "一生所爱",
    "album": "陶笛欣赏",
    "url": "http://fdfs.xmcdn.com/group13/M02/2B/2D/wKgDXlbxyF-hoIndABgWtE0tsmw788.mp3",
  }, {
    "name": "2048章",
    "album": "仙逆",
    "url": "http://audio.xmcdn.com/group10/M06/55/42/wKgDaVci_qzSZ7VEAFmBiJoJ1sA169.m4a",
  }, {
    "name": "第一集",
    "album": "农门丑妇",
    "url": "http://180j.ysts8.com:8000/%E7%8E%84%E5%B9%BB%E5%B0%8F%E8%AF%B4/%E5%86%9C%E9%97%A8%E4%B8%91%E5%A6%87/001.mp3?10103822300042x1552136997x10103828430702-8b36ee21c1ede456e2e84dd0d55fad99?3",
    "headers": "{\"User-Agent\": \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36\"}",
  }
];

void main() {
  runApp(new MaterialApp(home: new ExampleApp()));
}

class ExampleApp extends StatefulWidget {
  @override
  _ExampleAppState createState() => new _ExampleAppState();
}

class _ExampleAppState extends State<ExampleApp> {
  AudioPlayer player;
  String localFilePath;
  AudioPlayerState state;
  int songIndex = 0;
  int duration = 0;
  int position = 0;
  int buffer = 0;

  @override
  void initState() {
    super.initState();
    player = new AudioPlayer();
    player.init();
    player.durationHandler = (d) => setState(() {duration = d;});
    player.positionHandler = (p) => setState(() {
      position = p;
    });
    player.bufferHandler = (p) => setState(() {buffer = p;});
    player.errorHandler = (msg) {
      print('audioPlayer error : $msg');
      setState(() {
        this.state = AudioPlayerState.STOPPED;
        duration = 0;
        position = 0;
      });
    };
    player.audioPlayerStateChangeHandler = (AudioPlayerState state) {
      if(state == AudioPlayerState.COMPLETED) {
        state = AudioPlayerState.STOPPED;
        position = duration;
      }else if(state == AudioPlayerState.STOPPED) {
        position = 0;
      }
      setState(() {
        this.state = state;
      });
    };
  }

  @override
  void dispose(){
    player.dispose();
    super.dispose();
  }

  String get positionText => position == null ? '00:00': renderTime(position);
  String get durationText => duration == null ? '00:00': renderTime(duration);

  void _reset() {
    setState(() {
      duration = 0;
      position = 0;
      buffer = 0;
    });
  }

  Future<String> _loadFile(String url) async {
    String name = hex.encode(crypto.md5.convert(utf8.encode(url)).bytes);
    Directory dir = await getTemporaryDirectory();
    String path = '${dir.path}/audio_cache/$name';
    File file = File(path);
    if(await file.exists()) {
      return path;
    }
    await file.create(recursive: true);
    final bytes = await readBytes(url);
    await file.writeAsBytes(bytes);
    return path;
  }

  Future<void> _cleanCachedFiles() async {
    Directory dir = await getTemporaryDirectory();
    await Directory('${dir.path}/audio_cache').delete(recursive: true);
  }

  Widget _tab(List<Widget> children) {
    return Center(
      child: Container(
        padding: EdgeInsets.all(16.0),
        child: Column(
          children: children
              .map((w) => Container(child: w, padding: EdgeInsets.all(6.0)))
              .toList(),
        ),
      ),
    );
  }

  Widget _progress() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: <Widget>[
        new Text(
          '${positionText}',
          style: new TextStyle(fontSize: 24.0),
        ),
        Expanded(
          child: Slider(
            value: (position??0).toDouble(),
            onChanged: (value) => player.seek(value.toInt()),
            min: 0,
            max: (duration??0).toDouble(),
          ),
        ),
        new Text(
          '${durationText}',
          style: new TextStyle(fontSize: 24.0),
        ),
      ],
    );
  }

  Widget _controller() {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        new Padding(
          padding: new EdgeInsets.all(12.0),
          child: new Stack(
            children: [
              new CircularProgressIndicator(
                value: 1.0,
                valueColor: new AlwaysStoppedAnimation(Colors.grey[300]),
              ),
              new CircularProgressIndicator(
                value: duration > 0 && position != null && position > 0
                    ? position / duration
                    : 0.0,
                valueColor: new AlwaysStoppedAnimation(Colors.cyan),
              ),
            ],
          ),
        ),
        new IconButton(
            onPressed: () {
              if(state == AudioPlayerState.PLAYING) {
                player.pause();
                return;
              }
              if(state == AudioPlayerState.PAUSED) {
                player.resume();
                return;
              }
              dynamic song = songs[songIndex];
              _play(song);
            },
            iconSize: 64.0,
            icon: new Icon(state == AudioPlayerState.PLAYING ? Icons.pause : Icons.play_arrow),
            color: Colors.cyan,
        ),
        new IconButton(
            onPressed: state == AudioPlayerState.PLAYING || state == AudioPlayerState.PAUSED ? () => player.stop() : null,
            iconSize: 64.0,
            icon: new Icon(Icons.stop),
            color: Colors.cyan),
      ],
    );
  }

  void _play(dynamic song) async {
    String url = song['cache'] == true ? await _loadFile(song['url']) : song['url'];
    player.play(url, headers: song['headers']);
    player.updateNotification(title: song['name'], subtitle: song['album']);
    _reset();
  }

  void _setUrl(dynamic song) async {
    String url = song['cache'] == true ? await _loadFile(song['url']) : song['url'];
    player.setUrl(url, headers: song['headers']);
    player.updateNotification(title: song['name'], subtitle: song['album']);
    _reset();
  }

  Widget _list() {
    List<Widget> items = List();
    for(dynamic song in songs) {
      items.add(ListTile(
        onTap: () => _setUrl(song),
        title: Text(song['name']),
        subtitle: Text(song['album']),
      ),);
    }
    return Column(
      children: items,
    );
  }

  Widget _volumeControllers() {
    List<double> volumes = [0, 0.5, 1.0, 2.0];
    List<Widget> items = List();
    for(double volume in volumes) {
      items.add(RaisedButton(
        onPressed: () => player.setVolume(volume),
        child: Text('$volume'),
      ));
    }
    return Container(
      child: Column(
        children: <Widget>[
          Container(
            alignment: Alignment.centerLeft,
            child: Text('Volume', style: Theme.of(context).textTheme.title,),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: items,
          ),
        ],
      ),
    );
  }

  Widget _notificationTheme() {
    List themes = [
      {
        'label': 'dark',
        'titleColor': 'white',
        'subtitleColor': 'white',
        'backgroundColor': 'black',
      },
      {
        'label': 'light',
        'titleColor': 'black',
        'subtitleColor': 'black',
        'backgroundColor': '#f1f1f1',
      },
    ];
    List<Widget> items = List();
    for(dynamic theme in themes) {
      items.add(RaisedButton(
        onPressed: () => player.updateNotificationTheme(titleColor: theme['titleColor'], subtitleColor: theme['subtitleColor'], backgroundColor: theme['backgroundColor']),
        child: Text(theme['label'],),
      ));
    }
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: items,
    );
  }

  Widget _status() {
    return Container(
      child: Text('Player Status: $state'),
    );
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 4,
      child: Scaffold(
        appBar: AppBar(
          title: Text('audio player Example'),
        ),
        body: Column(
          children: [
            Container(
              margin: EdgeInsets.all(20),
              child: PlayerSlider(
                bufferPercent: buffer/100,
                playPercent: position == null || duration == 0 ? 0 : position /duration,
                onChange: (value) {
                  player.seek((value * duration).toInt());
                },
              ),
            ),
            _progress(),
            _status(),
            _controller(),
            _list(),
            Container(
              child: RaisedButton(
                  onPressed: _cleanCachedFiles,
                child: Text('Clear Cache'),
              ),
            ),
            _notificationTheme(),
            _volumeControllers(),
          ],
        ),
      ),
    );
  }

  String renderTime(int time) {
    time = time ~/ 1000;
    int seconds = time % 60;
    int minutes = time ~/ 60;
    int hours = minutes ~/ 60;
    minutes = minutes % 60;
    return (hours > 0 ? ((hours < 10 ? '0':'') + '$hours:'):'') + ((minutes < 10 ? '0':'') + '$minutes:') + ((seconds < 10 ? '0':'') + '$seconds');
  }
}
