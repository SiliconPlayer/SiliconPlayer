import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const SiliconPlayerApp());
}

class SiliconPlayerApp extends StatelessWidget {
  const SiliconPlayerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SiliconPlayer',
      theme: ThemeData(
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF64FFDA),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
        fontFamily: 'Roboto',
      ),
      home: const PlayerControlPage(),
    );
  }
}

class PlayerControlPage extends StatefulWidget {
  const PlayerControlPage({super.key});

  @override
  State<PlayerControlPage> createState() => _PlayerControlPageState();
}

class _PlayerControlPageState extends State<PlayerControlPage> {
  static const _channel = MethodChannel('com.flopster101.siliconplayer/playback');

  final _pathController = TextEditingController(text: '/sdcard/Download/test.mod');
  
  String _title = 'No Track Loaded';
  String _artist = '';
  double _duration = 0.0;
  double _position = 0.0;
  bool _isPlaying = false;
  String _decoder = 'None';
  
  Timer? _updateTimer;
  bool _isSeeking = false;

  @override
  void initState() {
    super.initState();
    _startMetadataPolling();
  }

  @override
  void dispose() {
    _updateTimer?.cancel();
    _pathController.dispose();
    super.dispose();
  }

  void _startMetadataPolling() {
    _updateTimer = Timer.periodic(const Duration(milliseconds: 500), (timer) {
      if (!_isSeeking) {
        _fetchMetadata();
      }
    });
  }

  Future<void> _fetchMetadata() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('getMetadata');
      if (result != null) {
        setState(() {
          _title = result['title'] as String? ?? 'Unknown Title';
          _artist = result['artist'] as String? ?? 'Unknown Artist';
          _duration = (result['duration'] as num? ?? 0.0).toDouble();
          _position = (result['position'] as num? ?? 0.0).toDouble();
          _isPlaying = result['isPlaying'] as bool? ?? false;
          _decoder = result['decoder'] as String? ?? 'None';
        });
      }
    } on PlatformException catch (e) {
      debugPrint("Error fetching metadata: ${e.message}");
    }
  }

  Future<void> _play() async {
    final path = _pathController.text.trim();
    if (path.isEmpty) return;
    try {
      await _channel.invokeMethod('play', {'path': path});
      _fetchMetadata();
    } on PlatformException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _pause() async {
    try {
      await _channel.invokeMethod('pause');
      _fetchMetadata();
    } on PlatformException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _stop() async {
    try {
      await _channel.invokeMethod('stop');
      setState(() {
        _title = 'No Track Loaded';
        _artist = '';
        _duration = 0.0;
        _position = 0.0;
        _isPlaying = false;
        _decoder = 'None';
      });
    } on PlatformException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _next() async {
    try {
      await _channel.invokeMethod('next');
      _fetchMetadata();
    } on PlatformException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _prev() async {
    try {
      await _channel.invokeMethod('prev');
      _fetchMetadata();
    } on PlatformException catch (e) {
      _showError(e.message);
    }
  }

  Future<void> _seek(double seconds) async {
    try {
      await _channel.invokeMethod('seek', {'seconds': seconds});
    } on PlatformException catch (e) {
      _showError(e.message);
    }
  }

  void _showError(String? message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Error: ${message ?? 'Unknown error'}')),
    );
  }

  String _formatTime(double seconds) {
    if (seconds.isNaN || seconds.isInfinite || seconds <= 0) return '0:00';
    final m = (seconds / 60).floor();
    final s = (seconds % 60).floor().toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('SiliconPlayer (Flutter Test)'),
        centerTitle: true,
        backgroundColor: theme.colorScheme.surfaceContainer,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Track Info Card
            Card(
              elevation: 4,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  children: [
                    Text(
                      _title.isEmpty ? 'Untitled Track' : _title,
                      style: theme.textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.primary,
                      ),
                      textAlign: TextAlign.center,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _artist.isEmpty ? 'Unknown Artist' : _artist,
                      style: theme.textTheme.bodyLarge?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 16),
                    Chip(
                      label: Text('Decoder: $_decoder'),
                      backgroundColor: theme.colorScheme.secondaryContainer,
                      labelStyle: TextStyle(color: theme.colorScheme.onSecondaryContainer),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Progress Slider
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(_formatTime(_position)),
                Text(_formatTime(_duration)),
              ],
            ),
            Slider(
              value: _position.clamp(0.0, _duration > 0.0 ? _duration : 1.0),
              min: 0.0,
              max: _duration > 0.0 ? _duration : 1.0,
              onChanged: (val) {
                setState(() {
                  _isSeeking = true;
                  _position = val;
                });
              },
              onChangeEnd: (val) async {
                await _seek(val);
                setState(() {
                  _isSeeking = false;
                });
              },
            ),
            const SizedBox(height: 16),

            // Media Controls
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                IconButton(
                  iconSize: 36,
                  icon: const Icon(Icons.skip_previous),
                  onPressed: _prev,
                ),
                const SizedBox(width: 16),
                FloatingActionButton.large(
                  onPressed: _isPlaying ? _pause : _play,
                  child: Icon(_isPlaying ? Icons.pause : Icons.play_arrow),
                ),
                const SizedBox(width: 16),
                IconButton(
                  iconSize: 36,
                  icon: const Icon(Icons.stop),
                  onPressed: _stop,
                ),
                const SizedBox(width: 16),
                IconButton(
                  iconSize: 36,
                  icon: const Icon(Icons.skip_next),
                  onPressed: _next,
                ),
              ],
            ),
            const SizedBox(height: 32),

            // Path Loading Section
            TextField(
              controller: _pathController,
              decoration: const InputDecoration(
                labelText: 'Playable File Path (Local)',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.folder_open),
                helperText: 'Enter absolute path to music file (e.g. MOD, XM, S3M, SID, NSF, etc.)',
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: _play,
              icon: const Icon(Icons.music_note),
              label: const Text('Load & Play Track'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                backgroundColor: theme.colorScheme.primaryContainer,
                foregroundColor: theme.colorScheme.onPrimaryContainer,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
