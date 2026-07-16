import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'file_browser_screen.dart';
import 'visualizer_widget.dart';

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
          seedColor: const Color(0xFF00E676), // Bright neon green for retro/synthesizer vibe
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
        fontFamily: 'Roboto',
      ),
      home: const MainContainerPage(),
    );
  }
}

class MainContainerPage extends StatefulWidget {
  const MainContainerPage({super.key});

  @override
  State<MainContainerPage> createState() => _MainContainerPageState();
}

class _MainContainerPageState extends State<MainContainerPage> {
  static const _channel = MethodChannel('com.flopster101.siliconplayer/playback');

  // Track playback state variables
  String? _currentTrackPath;
  String _title = 'No Track Loaded';
  String _artist = '';
  double _duration = 0.0;
  double _position = 0.0;
  bool _isPlaying = false;
  String _decoder = 'None';
  String _backend = 'Unknown';
  int _subtuneCount = 0;
  int _currentSubtuneIndex = 0;
  double _masterGainDb = 0.0;

  // Real-time visualization state
  List<double> _visualizationBars = [];

  // Polling timers
  Timer? _statusTimer;
  Timer? _visualizerTimer;

  // UI State
  bool _isPlayerExpanded = false;
  bool _isSeeking = false;

  @override
  void initState() {
    super.initState();
    _fetchMasterGain();
    _startStatusPolling();
  }

  @override
  void dispose() {
    _statusTimer?.cancel();
    _visualizerTimer?.cancel();
    super.dispose();
  }

  // Periodic metadata/status polling
  void _startStatusPolling() {
    _statusTimer = Timer.periodic(const Duration(milliseconds: 150), (timer) {
      if (!_isSeeking) {
        _fetchStatus();
      }
    });
  }

  // Visualizer polling - runs at ~30fps only when player is expanded to optimize CPU
  void _startVisualizerPolling() {
    _visualizerTimer?.cancel();
    _visualizerTimer = Timer.periodic(const Duration(milliseconds: 33), (timer) {
      if (_isPlaying && _isPlayerExpanded) {
        _fetchVisualization();
      } else if (!_isPlayerExpanded) {
        timer.cancel();
      }
    });
  }

  Future<void> _fetchStatus() async {
    try {
      final status = await _channel.invokeMethod<Map<dynamic, dynamic>>('getMetadata');
      if (status != null) {
        setState(() {
          _title = status['title'] as String? ?? '';
          _artist = status['artist'] as String? ?? '';
          _duration = (status['duration'] as num? ?? 0.0).toDouble();
          _position = (status['position'] as num? ?? 0.0).toDouble();
          _isPlaying = status['isPlaying'] as bool? ?? false;
          _decoder = status['decoder'] as String? ?? 'None';
          _subtuneCount = status['subtuneCount'] as int? ?? 0;
          _currentSubtuneIndex = status['currentSubtuneIndex'] as int? ?? 0;

          // If a title/artist is loaded but currentTrackPath is null, set a placeholder so player displays
          if (_title.isNotEmpty && _title != 'No Track Loaded' && _currentTrackPath == null) {
            _currentTrackPath = 'active';
          }
        });
      }
    } on PlatformException catch (e) {
      debugPrint("Error fetching status: ${e.message}");
    }
  }

  Future<void> _fetchMasterGain() async {
    try {
      final gain = await _channel.invokeMethod<double>('getMasterGain');
      if (gain != null) {
        setState(() {
          _masterGainDb = gain;
        });
      }
    } catch (_) {}
  }

  Future<void> _fetchVisualization() async {
    try {
      final data = await _channel.invokeMethod<Map<dynamic, dynamic>>('getVisualization');
      if (data != null) {
        final barsData = data['bars'] as List<dynamic>?;
        if (barsData != null) {
          setState(() {
            _visualizationBars = barsData.map((e) => (e as num).toDouble()).toList();
          });
        }
      }
    } catch (_) {}
  }

  // Playback Control Handlers
  Future<void> _playFile(String path) async {
    try {
      await _channel.invokeMethod('play', {'path': path});
      setState(() {
        _currentTrackPath = path;
      });
      _fetchStatus();
      // Fetch detailed track metadata (such as backend, sample rate, etc.)
      final info = await _channel.invokeMethod<Map<dynamic, dynamic>>('getTrackMetadata');
      if (info != null) {
        setState(() {
          _backend = info['backend'] as String? ?? 'Unknown';
        });
      }
    } on PlatformException catch (e) {
      _showSnackbarError(e.message);
    }
  }

  Future<void> _togglePlayPause() async {
    try {
      if (_isPlaying) {
        await _channel.invokeMethod('pause');
      } else {
        // If we have a track path, resume it
        if (_currentTrackPath != null && _currentTrackPath != 'active') {
          await _channel.invokeMethod('play', {'path': _currentTrackPath});
        } else {
          // Fallback toggle
          await _channel.invokeMethod('pause');
        }
      }
      _fetchStatus();
    } on PlatformException catch (e) {
      _showSnackbarError(e.message);
    }
  }

  Future<void> _stop() async {
    try {
      await _channel.invokeMethod('stop');
      setState(() {
        _currentTrackPath = null;
        _title = 'No Track Loaded';
        _artist = '';
        _duration = 0.0;
        _position = 0.0;
        _isPlaying = false;
        _decoder = 'None';
        _isPlayerExpanded = false;
        _visualizationBars.clear();
      });
    } on PlatformException catch (e) {
      _showSnackbarError(e.message);
    }
  }

  Future<void> _next() async {
    try {
      await _channel.invokeMethod('next');
      _fetchStatus();
    } on PlatformException catch (e) {
      _showSnackbarError(e.message);
    }
  }

  Future<void> _prev() async {
    try {
      await _channel.invokeMethod('prev');
      _fetchStatus();
    } on PlatformException catch (e) {
      _showSnackbarError(e.message);
    }
  }

  Future<void> _seek(double seconds) async {
    try {
      await _channel.invokeMethod('seek', {'seconds': seconds});
      setState(() {
        _position = seconds;
      });
    } on PlatformException catch (e) {
      _showSnackbarError(e.message);
    }
  }

  Future<void> _selectSubtune(int index) async {
    try {
      await _channel.invokeMethod('selectSubtune', {'index': index});
      _fetchStatus();
    } on PlatformException catch (e) {
      _showSnackbarError(e.message);
    }
  }

  Future<void> _setMasterVolume(double db) async {
    try {
      await _channel.invokeMethod('setMasterGain', {'gain': db});
      setState(() {
        _masterGainDb = db;
      });
    } catch (_) {}
  }

  void _showSnackbarError(String? message) {
    if (message == null) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(context).colorScheme.error,
      ),
    );
  }

  String _formatDuration(double seconds) {
    if (seconds.isNaN || seconds.isInfinite) return '0:00';
    final int minutes = (seconds / 60).floor();
    final int remainingSeconds = (seconds % 60).floor();
    return '$minutes:${remainingSeconds.toString().padLeft(2, '0')}';
  }

  // Opens a subtunes selection bottom sheet dialog
  void _openSubtunePicker() {
    final theme = Theme.of(context);
    showModalBottomSheet(
      context: context,
      backgroundColor: theme.colorScheme.surfaceContainer,
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text(
                  'Select Subtune',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: ListView.builder(
                  itemCount: _subtuneCount,
                  itemBuilder: (context, index) {
                    final isCurrent = index == _currentSubtuneIndex;
                    return ListTile(
                      title: Text(
                        'Subtune ${index + 1}',
                        style: TextStyle(
                          fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
                          color: isCurrent ? theme.colorScheme.primary : null,
                        ),
                      ),
                      trailing: isCurrent ? Icon(Icons.check, color: theme.colorScheme.primary) : null,
                      onTap: () {
                        Navigator.pop(context);
                        _selectSubtune(index);
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final mediaQuery = MediaQuery.of(context);
    final screenHeight = mediaQuery.size.height;
    final topPadding = mediaQuery.padding.top;
    final bottomPadding = mediaQuery.padding.bottom;

    final isPlayerVisible = _currentTrackPath != null;

    return WillPopScope(
      onWillPop: () async {
        if (_isPlayerExpanded) {
          setState(() {
            _isPlayerExpanded = false;
          });
          return false;
        }
        return true;
      },
      child: Scaffold(
        body: Stack(
          children: [
            // 1. File Browser Base Screen
            Positioned.fill(
              child: Padding(
                padding: EdgeInsets.only(
                  bottom: isPlayerVisible ? 76.0 : 0.0, // avoid miniplayer overlapping lists
                ),
                child: FileBrowserScreen(
                  onFileSelected: _playFile,
                  currentPlayingPath: _currentTrackPath,
                ),
              ),
            ),

            // 2. Sliding/Expanding Player
            if (isPlayerVisible)
              AnimatedPositioned(
                duration: const Duration(milliseconds: 320),
                curve: Curves.fastOutSlowIn,
                left: 0,
                right: 0,
                bottom: 0,
                top: _isPlayerExpanded ? 0 : screenHeight - 76 - bottomPadding,
                child: GestureDetector(
                  onVerticalDragUpdate: (details) {
                    // Collapse on downward swipe
                    if (details.primaryDelta! > 8 && _isPlayerExpanded) {
                      setState(() {
                        _isPlayerExpanded = false;
                      });
                    }
                  },
                  child: Container(
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerHigh,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.4),
                          blurRadius: 10,
                          offset: const Offset(0, -2),
                        ),
                      ],
                      borderRadius: _isPlayerExpanded
                          ? BorderRadius.zero
                          : const BorderRadius.vertical(top: Radius.circular(16)),
                    ),
                    child: SafeArea(
                      top: _isPlayerExpanded,
                      bottom: true,
                      child: AnimatedCrossFade(
                        duration: const Duration(milliseconds: 200),
                        crossFadeState: _isPlayerExpanded
                            ? CrossFadeState.showSecond
                            : CrossFadeState.showFirst,
                        firstChild: _buildMiniplayer(theme),
                        secondChild: _buildFullscreenPlayer(theme, topPadding),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  // --- Miniplayer Layout ---
  Widget _buildMiniplayer(ThemeData theme) {
    return InkWell(
      onTap: () {
        setState(() {
          _isPlayerExpanded = true;
        });
        _startVisualizerPolling();
      },
      child: Container(
        height: 76,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          children: [
            // Edge-to-edge linear track progress bar
            SizedBox(
              height: 2,
              child: LinearProgressIndicator(
                value: _duration > 0 ? (_position / _duration).clamp(0.0, 1.0) : 0.0,
                backgroundColor: theme.colorScheme.surfaceContainerHighest,
                valueColor: AlwaysStoppedAnimation<Color>(theme.colorScheme.primary),
              ),
            ),
            Expanded(
              child: Row(
                children: [
                  // Animated equalizer/music icon
                  CircleAvatar(
                    backgroundColor: theme.colorScheme.primary.withOpacity(0.1),
                    foregroundColor: theme.colorScheme.primary,
                    child: const Icon(Icons.music_note),
                  ),
                  const SizedBox(width: 12),
                  // Title & Artist
                  Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _title.isEmpty ? 'Unknown Title' : _title,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          _artist.isEmpty ? 'Unknown Artist' : _artist,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  // Player mini-controls
                  IconButton(
                    icon: Icon(_isPlaying ? Icons.pause : Icons.play_arrow),
                    onPressed: _togglePlayPause,
                  ),
                  IconButton(
                    icon: const Icon(Icons.stop),
                    onPressed: _stop,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // --- Fullscreen Player Layout ---
  Widget _buildFullscreenPlayer(ThemeData theme, double topPadding) {
    return SingleChildScrollView(
      physics: const NeverScrollableScrollPhysics(),
      child: Container(
        height: MediaQuery.of(context).size.height - topPadding,
        padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Collapse handlebar / button
            Center(
              child: IconButton(
                icon: const Icon(Icons.keyboard_arrow_down, size: 36),
                tooltip: 'Minimize Player',
                onPressed: () {
                  setState(() {
                    _isPlayerExpanded = false;
                  });
                },
              ),
            ),
            
            // Header / App Title
            Center(
              child: Text(
                'SiliconPlayer (Flutter Test)',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                  color: theme.colorScheme.primary,
                  letterSpacing: 1.0,
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Visualizer Panel
            Expanded(
              flex: 4,
              child: Center(
                child: VisualizerWidget(bars: _visualizationBars),
              ),
            ),
            const SizedBox(height: 16),

            // Metadata Card
            Card(
              color: theme.colorScheme.surfaceContainer,
              elevation: 0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
                child: Column(
                  children: [
                    Text(
                      _title.isEmpty ? 'Unknown Title' : _title,
                      style: theme.textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                      textAlign: TextAlign.center,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _artist.isEmpty ? 'Unknown Artist' : _artist,
                      style: theme.textTheme.bodyLarge?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                      textAlign: TextAlign.center,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 12),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: theme.colorScheme.secondaryContainer,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            'DEC: $_decoder',
                            style: TextStyle(
                              fontSize: 11,
                              color: theme.colorScheme.onSecondaryContainer,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        if (_backend != 'Unknown') ...[
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                              color: theme.colorScheme.tertiaryContainer,
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              'OUT: $_backend',
                              style: TextStyle(
                                fontSize: 11,
                                color: theme.colorScheme.onTertiaryContainer,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
            ),

            // Subtune Selector Chip (if subtunes are available)
            if (_subtuneCount > 1) ...[
              const SizedBox(height: 12),
              Center(
                child: ActionChip(
                  avatar: const Icon(Icons.music_video, size: 16),
                  label: Text('Subtune ${_currentSubtuneIndex + 1} / $_subtuneCount'),
                  onPressed: _openSubtunePicker,
                ),
              ),
            ],

            const SizedBox(height: 16),

            // Seek slider and time counters
            Column(
              children: [
                SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    trackHeight: 4,
                    thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                    overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                  ),
                  child: Slider(
                    value: _position.clamp(0.0, _duration > 0 ? _duration : 0.0),
                    min: 0.0,
                    max: _duration > 0 ? _duration : 0.0,
                    activeColor: theme.colorScheme.primary,
                    inactiveColor: theme.colorScheme.surfaceContainerHighest,
                    onChangeStart: (_) {
                      setState(() {
                        _isSeeking = true;
                      });
                    },
                    onChanged: (val) {
                      setState(() {
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
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        _formatDuration(_position),
                        style: theme.textTheme.bodySmall,
                      ),
                      Text(
                        _formatDuration(_duration),
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // Playback controls row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                IconButton(
                  iconSize: 32,
                  icon: const Icon(Icons.skip_previous),
                  onPressed: _prev,
                ),
                IconButton(
                  iconSize: 48,
                  icon: Icon(_isPlaying ? Icons.pause_circle_filled : Icons.play_circle_filled),
                  color: theme.colorScheme.primary,
                  onPressed: _togglePlayPause,
                ),
                IconButton(
                  iconSize: 32,
                  icon: const Icon(Icons.stop_circle),
                  onPressed: _stop,
                ),
                IconButton(
                  iconSize: 32,
                  icon: const Icon(Icons.skip_next),
                  onPressed: _next,
                ),
              ],
            ),

            const SizedBox(height: 24),

            // Master Volume / Gain controller
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              child: Row(
                children: [
                  const Icon(Icons.volume_up, size: 20),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Slider(
                      value: _masterGainDb.clamp(-40.0, 6.0),
                      min: -40.0,
                      max: 6.0,
                      divisions: 46,
                      label: '${_masterGainDb.toStringAsFixed(1)} dB',
                      activeColor: theme.colorScheme.tertiary,
                      inactiveColor: theme.colorScheme.surfaceContainerHighest,
                      onChanged: _setMasterVolume,
                    ),
                  ),
                  SizedBox(
                    width: 60,
                    child: Text(
                      '${_masterGainDb >= -39.0 ? "${_masterGainDb.toStringAsFixed(1)} dB" : "Mute"}',
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                      textAlign: TextAlign.end,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}
