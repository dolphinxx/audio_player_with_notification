import 'dart:async';
import 'dart:io';

import 'package:audio_player_with_notification/audio_player.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart';
import 'package:path_provider/path_provider.dart';

typedef void OnError(Exception exception);

const kUrl1 = 'http://222.216.30.118:8088/201411542.mp3';
const kUrl2 = 'http://222.216.30.118:8088/201412637.mp3';
const songs = [
  {
    "name": "一生所爱",
    "album": "电影《大话西游》插曲",
    "url": "http://222.216.30.118:8088/201411542.mp3",
  }, {
    "name": "倩女幽魂",
    "album": "张国荣电影歌集",
    "url": "http://222.216.30.118:8088/201412637.mp3",
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
  int duration;
  int position;

  @override
  void initState() {
    super.initState();
    player = new AudioPlayer();
    player.durationHandler = (d) => setState(() {duration = d;});
    player.positionHandler = (p) => setState(() {
      position = p;
    });
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

  String get positionText => position == null ? '00:00': renderTime(position);
  String get durationText => duration == null ? '00:00': renderTime(duration);

  Future _loadFile() async {
    final bytes = await readBytes(kUrl1);
    final dir = await getApplicationDocumentsDirectory();
    final file = new File('${dir.path}/audio.mp3');

    await file.writeAsBytes(bytes);
    if (await file.exists()) {
      setState(() {
        localFilePath = file.path;
      });
    }
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

  Widget localFile() {
    return _tab([
      Text('File: $kUrl1'),
      RaisedButton(
        onPressed: () => _loadFile(),
        child: Text('Download File to your Device'),
      ),
      Text('Current local file path: $localFilePath'),
//      localFilePath == null
//          ? Container()
//          : PlayerWidget(url: localFilePath, isLocal: true),
    ]);
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
                value: position != null && position > 0
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

  void _play(dynamic song) {
    player.play(song['url']);
    player.updateNotification(title: song['name'], subtitle: song['album']);
  }

  void _setUrl(dynamic song) {
    player.setUrl(song['url']);
    player.updateNotification(title: song['name'], subtitle: song['album']);
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
            _progress(),
            _status(),
            _controller(),
            _list(),
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
