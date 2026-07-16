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
        if (_currentTrackPath != null && _currentTrackPath != 'active') {
          await _playFile(_currentTrackPath!);
        } else {
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

    final isPlayerActive = _currentTrackPath != null;

    // Miniplayer / Fullscreen Position states
    final double left = _isPlayerExpanded ? 0 : 14;
    final double right = _isPlayerExpanded ? 0 : 14;
    final double bottom = _isPlayerExpanded ? 0 : bottomPadding + 8;
    final double? top = _isPlayerExpanded ? 0 : null;
    final double? height = _isPlayerExpanded ? null : 76;

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
            // 1. File Browser Screen
            Positioned.fill(
              child: Padding(
                padding: EdgeInsets.only(
                  bottom: isPlayerActive ? 92.0 : 0.0, // Space so list doesn't overlap the floating miniplayer
                ),
                child: FileBrowserScreen(
                  onFileSelected: _playFile,
                  currentPlayingPath: _currentTrackPath,
                ),
              ),
            ),

            // 2. Translucent Floating Miniplayer / Fullscreen Morph Card
            if (isPlayerActive)
              AnimatedPositioned(
                duration: const Duration(milliseconds: 320),
                curve: Curves.fastOutSlowIn,
                left: left,
                right: right,
                bottom: bottom,
                top: top,
                height: height,
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
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.4),
                          blurRadius: 10,
                          offset: const Offset(0, -2),
                        ),
                      ],
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
                        bottom: false, // handled manually in Stack for progress indicator
                        left: _isPlayerExpanded,
                        right: _isPlayerExpanded,
                        child: AnimatedCrossFade(
                          duration: const Duration(milliseconds: 200),
                          crossFadeState: _isPlayerExpanded
                              ? CrossFadeState.showSecond
                              : CrossFadeState.showFirst,
                          firstChild: _buildFloatingMiniplayer(theme, bottomPadding),
                          secondChild: _buildFullscreenPlayer(theme, screenWidth),
                        ),
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

  // --- Translucent Floating Miniplayer ---
  Widget _buildFloatingMiniplayer(ThemeData theme, double bottomPadding) {
    final formatLabel = _currentTrackPath != 'active' && _currentTrackPath != null
        ? FileExtensionHeuristics.inferredPrimaryExtensionForName(_currentTrackPath!)?.toUpperCase() ?? 'UNK'
        : 'TRACK';
    
    final accentColor = FileExtensionHeuristics.getExtensionColor(formatLabel.toLowerCase(), theme);
    final miniIcon = FileExtensionHeuristics.getExtensionIcon(formatLabel.toLowerCase());

    final progress = _duration > 0 ? (_position / _duration).clamp(0.0, 1.0) : 0.0;

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
                          _title.isEmpty ? 'Unknown Title' : _title,
                          style: theme.textTheme.titleSmall?.copyWith(
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
  Widget _buildFullscreenPlayer(ThemeData theme, double screenWidth) {
    // Determine the size of the square visualizer card to fit beautifully
    final double visualizerCardSize = (screenWidth - 48).clamp(200.0, 420.0);

    final formatLabel = _currentTrackPath != 'active' && _currentTrackPath != null
        ? FileExtensionHeuristics.inferredPrimaryExtensionForName(_currentTrackPath!)?.toUpperCase() ?? 'UNK'
        : 'TRACK';

    final filename = _currentTrackPath != null && _currentTrackPath != 'active'
        ? p.basename(_currentTrackPath!)
        : 'active_track';

    final accentColor = FileExtensionHeuristics.getExtensionColor(formatLabel.toLowerCase(), theme);
    final detailIcon = FileExtensionHeuristics.getExtensionIcon(formatLabel.toLowerCase());

    return Container(
      color: const Color(0xFF121212), // Clean flat dark background
      child: Column(
        children: [
          // Header / Top bar (Down chevron only!)
          AppBar(
            backgroundColor: Colors.transparent,
            elevation: 0,
            leading: IconButton(
              icon: const Icon(Icons.keyboard_arrow_down, size: 36),
              tooltip: 'Minimize Player',
              onPressed: () {
                setState(() {
                  _isPlayerExpanded = false;
                });
              },
            ),
          ),
          
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  const SizedBox(height: 12),
                  
                  // Central SQUARE Visualizer / Artwork Card (exactly like Compose layout!)
                  Container(
                    width: visualizerCardSize,
                    height: visualizerCardSize,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.3),
                          blurRadius: 16,
                          offset: const Offset(0, 8),
                        ),
                      ],
                    ),
                    child: _isPlaying 
                        ? VisualizerWidget(bars: _visualizationBars)
                        : Card(
                            margin: EdgeInsets.zero,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                            color: const Color(0xFF2E2E2E), // Solid dark gray artwork card background
                            child: Center(
                              child: Icon(
                                detailIcon,
                                size: 84,
                                color: accentColor.withOpacity(0.7),
                              ),
                            ),
                          ),
                  ),
                  
                  const SizedBox(height: 28),

                  // Title (space_debris)
                  Text(
                    _title.isEmpty ? 'Unknown Title' : _title,
                    style: theme.textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                    textAlign: TextAlign.center,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  
                  // Artist (Unknown Artist)
                  Text(
                    _artist.isEmpty ? 'Unknown Artist' : _artist,
                    style: theme.textTheme.bodyLarge?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                    textAlign: TextAlign.center,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),

                  // Filename (space_debris.mod)
                  Text(
                    filename,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant.withOpacity(0.6),
                    ),
                    textAlign: TextAlign.center,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  
                  const SizedBox(height: 20),

                  // Tech specs lines (exactly like screenshot 3!)
                  // Line 1: format description (e.g. ProTracker MOD (M.K.))
                  Text(
                    _decoderDescription.isEmpty ? 'Audio Track' : _decoderDescription,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      fontWeight: FontWeight.w500,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 2),
                  // Line 2: size, sample rate, channels (e.g. 339.4 KB • 44.1 kHz • 4 ch)
                  Text(
                    '$_trackSizeLabel • ${(_sampleRateHz / 1000).toStringAsFixed(1)} kHz • ${_channelCount == 1 ? "Mono" : _channelCount == 2 ? "Stereo" : "$_channelCount ch"}${_bitDepthLabel.isNotEmpty ? " • $_bitDepthLabel" : ""}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant.withOpacity(0.8),
                    ),
                    textAlign: TextAlign.center,
                  ),

                  // Subtune selector chip
                  if (_subtuneCount > 1) ...[
                    const SizedBox(height: 16),
                    ActionChip(
                      avatar: const Icon(Icons.music_video, size: 16),
                      label: Text('Subtune ${_currentSubtuneIndex + 1} / $_subtuneCount'),
                      onPressed: _openSubtunePicker,
                    ),
                  ],

                  const SizedBox(height: 28),

                  // Seek slider and time counters
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

                  const SizedBox(height: 24),

                  // Transport controls row (Exactly matching screenshot 3!)
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      // Stop button (Grey circle container containing white square stop icon)
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
                      
                      // Prev button (Grey icon)
                      IconButton(
                        iconSize: 28,
                        icon: const Icon(Icons.skip_previous),
                        color: theme.colorScheme.onSurfaceVariant,
                        onPressed: _prev,
                      ),
                      
                      // Play/Pause button (Large blue circle containing white play/pause)
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
                      
                      // Next button (Grey icon)
                      IconButton(
                        iconSize: 28,
                        icon: const Icon(Icons.skip_next),
                        color: theme.colorScheme.onSurfaceVariant,
                        onPressed: _next,
                      ),
                      
                      // Repeat mode button (Grey circle containing loop icon)
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

                  const SizedBox(height: 36),

                  // Bottom Action Strip (Exactly matching the 7 icons in screenshot 3!)
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.analytics_outlined),
                          tooltip: 'Visualizer Mode',
                          onPressed: () {},
                        ),
                        IconButton(
                          icon: const Icon(Icons.settings),
                          tooltip: 'Core Settings',
                          onPressed: () {},
                        ),
                        IconButton(
                          icon: const Icon(Icons.playlist_play),
                          tooltip: 'Playlist',
                          onPressed: () {},
                        ),
                        IconButton(
                          icon: const Icon(Icons.star_border),
                          tooltip: 'Favorite',
                          onPressed: () {},
                        ),
                        IconButton(
                          icon: const Icon(Icons.info_outline),
                          tooltip: 'Track Info',
                          onPressed: () {},
                        ),
                        IconButton(
                          icon: const Icon(Icons.tune),
                          tooltip: 'Equalizer / Effects',
                          onPressed: () {},
                        ),
                        IconButton(
                          icon: const Icon(Icons.waves),
                          tooltip: 'Channel Controls',
                          onPressed: () {},
                        ),
                      ],
                    ),
                  ),
                  
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
