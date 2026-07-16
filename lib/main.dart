import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'file_browser_screen.dart';
import 'visualizer_widget.dart';
import 'file_extension_heuristics.dart';

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
          seedColor: const Color(0xFF1976D2), // Match the Compose blue/indigo accent!
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

  // Playback state variables
  String? _currentTrackPath;
  String _title = 'No Track Loaded';
  String _artist = '';
  double _duration = 0.0;
  double _position = 0.0;
  bool _isPlaying = false;
  String _decoder = 'None';
  String _decoderDescription = '';
  String _backend = 'Unknown';
  int _subtuneCount = 0;
  int _currentSubtuneIndex = 0;
  double _masterGainDb = 0.0;
  String _trackSizeLabel = '0.0 KB';

  // Metadata technical specifications
  int _sampleRateHz = 0;
  int _channelCount = 0;
  String _bitDepthLabel = '';

  // Real-time visualization state
  List<double> _visualizationBars = [];

  // Polling timers
  Timer? _statusTimer;
  Timer? _visualizerTimer;

  // UI States
  bool _isPlayerExpanded = false;
  bool _isSeeking = false;
  bool _showVisualizer = false;

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

  void _startStatusPolling() {
    _statusTimer = Timer.periodic(const Duration(milliseconds: 150), (timer) {
      if (!_isSeeking) {
        _fetchStatus();
      }
    });
  }

  void _startVisualizerPolling() {
    _visualizerTimer?.cancel();
    if (!_showVisualizer) return;
    _visualizerTimer = Timer.periodic(const Duration(milliseconds: 33), (timer) {
      if (_isPlaying && _isPlayerExpanded && _showVisualizer) {
        _fetchVisualization();
      } else {
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
          _decoderDescription = status['decoderDescription'] as String? ?? '';
          _subtuneCount = status['subtuneCount'] as int? ?? 0;
          _currentSubtuneIndex = status['currentSubtuneIndex'] as int? ?? 0;

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

  Future<void> _playFile(String path) async {
    try {
      setState(() {
        _isLoadingPlay = true;
      });
      
      // Calculate file size
      try {
        final file = File(path);
        final sizeBytes = await file.length();
        _trackSizeLabel = _formatSize(sizeBytes);
      } catch (_) {
        _trackSizeLabel = '0.0 KB';
      }

      // Load & play asynchronously via coroutine-backed MethodChannel
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('play', {'path': path});
      
      setState(() {
        _isLoadingPlay = false;
        _currentTrackPath = path;
        
        if (result != null) {
          _title = result['title'] as String? ?? '';
          _artist = result['artist'] as String? ?? '';
          _duration = (result['duration'] as num? ?? 0.0).toDouble();
          _position = (result['position'] as num? ?? 0.0).toDouble();
          _decoder = result['decoder'] as String? ?? 'None';
          _decoderDescription = result['decoderDescription'] as String? ?? '';
          _subtuneCount = result['subtuneCount'] as int? ?? 0;
          _currentSubtuneIndex = result['currentSubtuneIndex'] as int? ?? 0;
          _sampleRateHz = result['sampleRate'] as int? ?? 0;
          _channelCount = result['channels'] as int? ?? 0;
          _bitDepthLabel = result['bitDepthLabel'] as String? ?? '';
          _backend = result['backend'] as String? ?? 'Unknown';
          _isPlaying = true;
        }
      });
    } on PlatformException catch (e) {
      setState(() {
        _isLoadingPlay = false;
      });
      _showSnackbarError(e.message);
    }
  }

  bool _isLoadingPlay = false;

  Future<void> _togglePlayPause() async {
    try {
      if (_isPlaying) {
        await _channel.invokeMethod('pause');
      } else {
        await _channel.invokeMethod('resume');
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
        _decoderDescription = '';
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
    if (seconds.isNaN || seconds.isInfinite || seconds < 0.0) return '00:00';
    final int minutes = (seconds / 60).floor();
    final int remainingSeconds = (seconds % 60).floor();
    return '${minutes.toString().padLeft(2, '0')}:${remainingSeconds.toString().padLeft(2, '0')}';
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

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
    final screenWidth = mediaQuery.size.width;
    final bottomPadding = mediaQuery.padding.bottom;

    final bool showPlayer = _currentTrackPath != null;

    // Miniplayer / Fullscreen Position states
    final double left = _isPlayerExpanded ? 0 : 14;
    final double right = _isPlayerExpanded ? 0 : 14;
    final double bottom = _isPlayerExpanded
        ? 0
        : showPlayer
            ? bottomPadding + 8
            : -100;
    final double top = _isPlayerExpanded
        ? 0
        : showPlayer
            ? (screenHeight - 76 - bottomPadding - 8)
            : screenHeight;

    return PopScope(
      canPop: !_isPlayerExpanded,
      onPopInvoked: (didPop) {
        if (didPop) return;
        if (_isPlayerExpanded) {
          setState(() {
            _isPlayerExpanded = false;
          });
        }
      },
      child: Scaffold(
        body: Stack(
          children: [
            // 1. File Browser Screen
            Positioned.fill(
              child: Padding(
                padding: EdgeInsets.only(
                  bottom: showPlayer ? 92.0 : 0.0, // Space so list doesn't overlap the floating miniplayer
                ),
                child: FileBrowserScreen(
                  onFileSelected: _playFile,
                  currentPlayingPath: _currentTrackPath,
                ),
              ),
            ),

            // 2. Translucent Floating Miniplayer / Fullscreen Morph Card
            AnimatedPositioned(
              duration: const Duration(milliseconds: 320),
              curve: Curves.fastOutSlowIn,
              left: left,
              right: right,
              bottom: bottom,
              top: top,
              child: IgnorePointer(
                ignoring: !showPlayer,
                child: GestureDetector(
                  onVerticalDragUpdate: (details) {
                    if (details.primaryDelta! > 8 && _isPlayerExpanded) {
                      setState(() {
                        _isPlayerExpanded = false;
                      });
                    }
                  },
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 320),
                    curve: Curves.fastOutSlowIn,
                    decoration: BoxDecoration(
                      color: const Color(0xFF212121), // Dark Slate/Gray matching screenshot
                      borderRadius: BorderRadius.circular(_isPlayerExpanded ? 0 : 16),
                      border: Border.all(
                        color: _isPlayerExpanded 
                            ? Colors.transparent 
                            : theme.colorScheme.onSurface.withOpacity(0.08),
                        width: 1.0,
                      ),
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(_isPlayerExpanded ? 0 : 16),
                      child: SafeArea(
                        top: _isPlayerExpanded,
                        bottom: _isPlayerExpanded, // respects bottom safe area when fullscreen
                        left: _isPlayerExpanded,
                        right: _isPlayerExpanded,
                        child: AnimatedCrossFade(
                          duration: const Duration(milliseconds: 200),
                          crossFadeState: _isPlayerExpanded
                              ? CrossFadeState.showSecond
                              : CrossFadeState.showFirst,
                          firstChild: _buildFloatingMiniplayer(theme, bottomPadding),
                          secondChild: _buildFullscreenPlayer(theme, screenWidth, screenHeight),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),],
        ),
      ),
    );
  }

  // --- Translucent Floating Miniplayer ---
  Widget _buildFloatingMiniplayer(ThemeData theme, double bottomPadding) {
    final formatLabel = _currentTrackPath != 'active' && _currentTrackPath != null
        ? FileExtensionHeuristics.inferredPrimaryExtensionForName(_currentTrackPath!)?.toUpperCase() ?? 'UNK'
        : 'TRACK';
    
    final accentColor = FileExtensionHeuristics.getExtensionColor(formatLabel.toLowerCase(), theme);
    final miniIcon = FileExtensionHeuristics.getExtensionIcon(formatLabel.toLowerCase());

    final progress = _duration > 0 ? (_position / _duration).clamp(0.0, 1.0) : 0.0;

    final String displayTitle = _title.isNotEmpty && _title != 'No Track Loaded'
        ? _title
        : _currentTrackPath != null && _currentTrackPath != 'active'
            ? FileExtensionHeuristics.inferredDisplayTitleForName(p.basename(_currentTrackPath!))
            : 'Unknown Title';
            
    final String displayArtist = _artist.isNotEmpty && _artist != 'Unknown Artist'
        ? _artist
        : 'Unknown Artist';

    return InkWell(
      onTap: () {
        setState(() {
          _isPlayerExpanded = true;
        });
        _startVisualizerPolling();
      },
      borderRadius: BorderRadius.circular(16),
      child: SizedBox(
        height: 74,
        child: Stack(
          children: [
            // Miniplayer Row Contents
            Positioned(
              left: 12,
              right: 12,
              top: 0,
              bottom: 4, // leave small margin at bottom for progress bar
              child: Row(
                children: [
                  // Artwork / Format Icon with rounded corner box (46dp size)
                  Container(
                    width: 46,
                    height: 46,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(
                      miniIcon,
                      color: accentColor,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 10),
                  // Metadata & Time Display Column
                  Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          displayTitle,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          displayArtist,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          '$formatLabel • ${_formatDuration(_position)} / ${_formatDuration(_duration)}',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                            fontSize: 10,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  
                  // Audio Controls Row (Stop, Prev, Play/Pause, Next)
                  IconButton(
                    iconSize: 20,
                    icon: const Icon(Icons.stop),
                    onPressed: _stop,
                  ),
                  IconButton(
                    iconSize: 20,
                    icon: const Icon(Icons.skip_previous),
                    onPressed: _prev,
                  ),
                  IconButton(
                    iconSize: 22,
                    icon: Icon(_isPlaying ? Icons.pause : Icons.play_arrow),
                    onPressed: _togglePlayPause,
                  ),
                  IconButton(
                    iconSize: 20,
                    icon: const Icon(Icons.skip_next),
                    onPressed: _next,
                  ),
                ],
              ),
            ),
            
            // Linear Progress Indicator STRICTLY aligned at the bottom border of the card
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              height: 3,
              child: LinearProgressIndicator(
                value: progress,
                backgroundColor: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
                valueColor: AlwaysStoppedAnimation<Color>(theme.colorScheme.primary),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // --- Fullscreen Player ---
  Widget _buildFullscreenPlayer(ThemeData theme, double screenWidth, double screenHeight) {
    debugPrint('FullscreenPlayer Build: title="$_title", artist="$_artist", path="$_currentTrackPath"');
    
    // Calculate artwork card size based on screen width/height to fit on smaller screens
    final double maxArtworkSize = screenHeight - 420;
    final double visualizerCardSize = (screenWidth - 48).clamp(160.0, maxArtworkSize.clamp(160.0, 400.0));

    final formatLabel = _currentTrackPath != 'active' && _currentTrackPath != null
        ? FileExtensionHeuristics.inferredPrimaryExtensionForName(_currentTrackPath!)?.toUpperCase() ?? 'UNK'
        : 'TRACK';

    final filename = _currentTrackPath != null && _currentTrackPath != 'active'
        ? p.basename(_currentTrackPath!)
        : 'active_track';

    final accentColor = FileExtensionHeuristics.getExtensionColor(formatLabel.toLowerCase(), theme);
    final detailIcon = FileExtensionHeuristics.getExtensionIcon(formatLabel.toLowerCase());

    final String displayTitle = _title.isNotEmpty && _title != 'No Track Loaded'
        ? _title
        : _currentTrackPath != null && _currentTrackPath != 'active'
            ? FileExtensionHeuristics.inferredDisplayTitleForName(p.basename(_currentTrackPath!))
            : 'Unknown Title';
            
    final String displayArtist = _artist.isNotEmpty && _artist != 'Unknown Artist'
        ? _artist
        : 'Unknown Artist';

    return Container(
      color: const Color(0xFF121212), // Clean flat dark background
      child: Column(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Header / Top bar (Down chevron only!)
          Padding(
            padding: const EdgeInsets.only(top: 8.0, left: 8.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.start,
              children: [
                IconButton(
                  icon: const Icon(Icons.keyboard_arrow_down, size: 36),
                  tooltip: 'Minimize Player',
                  onPressed: () {
                    setState(() {
                      _isPlayerExpanded = false;
                    });
                  },
                ),
              ],
            ),
          ),
          
          // Player Content Column (No scroll view, fits perfectly!)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                // Central SQUARE Visualizer / Artwork Card
                Container(
                  width: visualizerCardSize,
                  height: visualizerCardSize,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(24),
                    color: const Color(0xFF2E2E2E),
                  ),
                  child: (_showVisualizer && _isPlaying)
                      ? VisualizerWidget(bars: _visualizationBars)
                      : Center(
                          child: Icon(
                            detailIcon,
                            size: visualizerCardSize * 0.25,
                            color: accentColor.withOpacity(0.7),
                          ),
                        ),
                ),
                
                const SizedBox(height: 20),

                // Title
                Text(
                  displayTitle,
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                
                // Artist
                Text(
                  displayArtist,
                  style: theme.textTheme.bodyLarge?.copyWith(
                    color: Colors.white.withOpacity(0.7),
                  ),
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),

                // Filename
                Text(
                  filename,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: Colors.white.withOpacity(0.5),
                  ),
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                
                const SizedBox(height: 16),

                // Tech specs line 1
                Text(
                  _decoderDescription.isEmpty ? 'Audio Track' : _decoderDescription,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: Colors.white.withOpacity(0.7),
                    fontWeight: FontWeight.w500,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 2),
                // Tech specs line 2
                Text(
                  '$_trackSizeLabel • ${(_sampleRateHz / 1000).toStringAsFixed(1)} kHz • ${_channelCount == 1 ? "Mono" : _channelCount == 2 ? "Stereo" : "$_channelCount ch"}${_bitDepthLabel.isNotEmpty ? " • $_bitDepthLabel" : ""}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: Colors.white.withOpacity(0.6),
                  ),
                  textAlign: TextAlign.center,
                ),

                // Subtune selector chip (conditional)
                if (_subtuneCount > 1) ...[
                  const SizedBox(height: 12),
                  ActionChip(
                    avatar: const Icon(Icons.music_video, size: 16),
                    label: Text('Subtune ${_currentSubtuneIndex + 1} / $_subtuneCount'),
                    onPressed: _openSubtunePicker,
                  ),
                ],
              ],
            ),
          ),

          // Controls Block at the Bottom
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Column(
              children: [
                // Seek slider
                Column(
                  children: [
                    SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        trackHeight: 4,
                        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                        overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                        activeTrackColor: theme.colorScheme.primary,
                        inactiveTrackColor: theme.colorScheme.surfaceContainerHighest,
                        thumbColor: theme.colorScheme.primary,
                      ),
                      child: Slider(
                        value: _position.clamp(0.0, _duration > 0 ? _duration : 0.0),
                        min: 0.0,
                        max: _duration > 0 ? _duration : 0.0,
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
                            style: theme.textTheme.bodySmall?.copyWith(color: Colors.white70),
                          ),
                          Text(
                            _formatDuration(_duration),
                            style: theme.textTheme.bodySmall?.copyWith(color: Colors.white70),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 16),

                // Transport Controls
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    // Stop button
                    CircleAvatar(
                      radius: 26,
                      backgroundColor: theme.colorScheme.surfaceContainerHighest,
                      foregroundColor: Colors.white,
                      child: IconButton(
                        iconSize: 22,
                        icon: const Icon(Icons.stop),
                        onPressed: _stop,
                      ),
                    ),
                    
                    // Prev button
                    IconButton(
                      iconSize: 28,
                      icon: const Icon(Icons.skip_previous),
                      color: Colors.white70,
                      onPressed: _prev,
                    ),
                    
                    // Play/Pause button
                    CircleAvatar(
                      radius: 36,
                      backgroundColor: theme.colorScheme.primary,
                      foregroundColor: Colors.white,
                      child: IconButton(
                        iconSize: 36,
                        icon: Icon(_isPlaying ? Icons.pause : Icons.play_arrow),
                        onPressed: _togglePlayPause,
                      ),
                    ),
                    
                    // Next button
                    IconButton(
                      iconSize: 28,
                      icon: const Icon(Icons.skip_next),
                      color: Colors.white70,
                      onPressed: _next,
                    ),
                    
                    // Repeat mode button
                    CircleAvatar(
                      radius: 26,
                      backgroundColor: theme.colorScheme.surfaceContainerHighest,
                      foregroundColor: Colors.white,
                      child: IconButton(
                        iconSize: 20,
                        icon: const Icon(Icons.repeat),
                        onPressed: () {},
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 24),

                // Bottom Action Strip
                Container(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerLow,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      IconButton(
                        icon: Icon(
                          Icons.analytics,
                          color: _showVisualizer ? theme.colorScheme.primary : Colors.white54,
                        ),
                        tooltip: 'Visualizer Mode',
                        onPressed: () {
                          setState(() {
                            _showVisualizer = !_showVisualizer;
                          });
                          if (_showVisualizer) {
                            _startVisualizerPolling();
                          } else {
                            _visualizerTimer?.cancel();
                          }
                        },
                      ),
                      IconButton(
                        icon: const Icon(Icons.settings, color: Colors.white70),
                        tooltip: 'Core Settings',
                        onPressed: () {},
                      ),
                      IconButton(
                        icon: const Icon(Icons.playlist_play, color: Colors.white70),
                        tooltip: 'Playlist',
                        onPressed: () {},
                      ),
                      IconButton(
                        icon: const Icon(Icons.star_border, color: Colors.white70),
                        tooltip: 'Favorite',
                        onPressed: () {},
                      ),
                      IconButton(
                        icon: const Icon(Icons.info_outline, color: Colors.white70),
                        tooltip: 'Track Info',
                        onPressed: () {},
                      ),
                      IconButton(
                        icon: const Icon(Icons.tune, color: Colors.white70),
                        tooltip: 'Equalizer / Effects',
                        onPressed: () {},
                      ),
                      IconButton(
                        icon: const Icon(Icons.waves, color: Colors.white70),
                        tooltip: 'Channel Controls',
                        onPressed: () {},
                      ),
                    ],
                  ),
                ),
                
                const SizedBox(height: 12),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
