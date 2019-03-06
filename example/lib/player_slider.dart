import 'package:flutter/material.dart';

class PlayerSlider extends StatelessWidget {
  final double bufferPercent;
  final double playPercent;
  final ValueChanged<double> onChange;
  final double height;
  final double trackHeight;
  final Color trackColor;
  final Color bufferColor;
  final Color playColor;

  PlayerSlider(
      {this.bufferPercent: 0,
      this.playPercent: 0,
      this.onChange,
      this.height: 13,
      this.trackHeight: 5,
      this.trackColor: Colors.lightBlueAccent,
      this.bufferColor: Colors.lightBlue,
      this.playColor: Colors.deepOrange});

  void _onChange(BuildContext context, Offset globalPosition) {
    if(onChange != null) {
      RenderBox box = (context.findRenderObject() as RenderBox);
      Offset local = box.globalToLocal(globalPosition);
//    print('global:${globalPosition.dx}, local: ${local.dx}, percent: ${local.dx * 100/box.size.width}');
      onChange(local.dx/box.size.width);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
//        color: Colors.blueGrey,
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onTapDown: (event) => _onChange(context, event.globalPosition),
        onHorizontalDragUpdate: (event) => _onChange(context, event.globalPosition),
        child: Stack(
          alignment: Alignment.centerLeft,
          children: <Widget>[
            Container(
              height: trackHeight,
              color: trackColor,
            ),
            FractionallySizedBox(
              alignment: Alignment.centerLeft,
              widthFactor: bufferPercent,
              child: Container(
                height: trackHeight,
                color: bufferColor,
              ),
            ),
            FractionallySizedBox(
              alignment: Alignment.centerLeft,
              widthFactor: playPercent,
              child: Container(
                height: trackHeight,
                color: playColor,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
