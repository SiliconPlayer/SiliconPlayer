import 'package:flutter/material.dart';

class VisualizerWidget extends StatelessWidget {
  final List<double> bars;

  const VisualizerWidget({
    super.key,
    required this.bars,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Container(
        width: double.infinity,
        height: double.infinity,
        color: Theme.of(context).colorScheme.surfaceContainerHighest.withOpacity(0.15),
        padding: const EdgeInsets.all(12),
        child: CustomPaint(
          painter: _VisualizerPainter(
            bars: bars,
            primaryColor: Theme.of(context).colorScheme.primary,
            secondaryColor: Theme.of(context).colorScheme.tertiary,
          ),
        ),
      ),
    );
  }
}

class _VisualizerPainter extends CustomPainter {
  final List<double> bars;
  final Color primaryColor;
  final Color secondaryColor;

  _VisualizerPainter({
    required this.bars,
    required this.primaryColor,
    required this.secondaryColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (bars.isEmpty) {
      // Draw silent/idle state bars
      final silentBars = List.generate(40, (index) => 0.05);
      _drawBars(canvas, size, silentBars);
      return;
    }

    _drawBars(canvas, size, bars);
  }

  void _drawBars(Canvas canvas, Size size, List<double> data) {
    final barCount = data.length;
    final spacing = 2.0;
    final totalSpacing = spacing * (barCount - 1);
    final barWidth = (size.width - totalSpacing) / barCount;

    final paint = Paint()
      ..style = PaintingStyle.fill;

    for (int i = 0; i < barCount; i++) {
      // Scale height: JNI bars are generally floats from 0.0 to 1.0
      double value = data[i].clamp(0.0, 1.0);
      final barHeight = size.height * value;

      final left = i * (barWidth + spacing);
      final top = size.height - barHeight;
      final right = left + barWidth;
      final bottom = size.height;

      // Draw rounded rectangle for a modern look
      final rect = RRect.fromRectAndRadius(
        Rect.fromLTRB(left, top, right, bottom),
        Radius.circular(barWidth / 2),
      );

      // Create a gradient for each bar or across the whole visualizer
      paint.shader = LinearGradient(
        colors: [primaryColor, secondaryColor],
        begin: Alignment.bottomCenter,
        end: Alignment.topCenter,
      ).createShader(Rect.fromLTRB(left, top, right, bottom));

      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _VisualizerPainter oldDelegate) {
    // Repaint whenever the bars list values change
    return oldDelegate.bars != bars;
  }
}
