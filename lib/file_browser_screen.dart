import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'file_extension_heuristics.dart';

class FileBrowserScreen extends StatefulWidget {
  final Function(String filePath) onFileSelected;
  final String? currentPlayingPath;

  const FileBrowserScreen({
    super.key,
    required this.onFileSelected,
    this.currentPlayingPath,
  });

  @override
  State<FileBrowserScreen> createState() => _FileBrowserScreenState();
}

class _FileBrowserScreenState extends State<FileBrowserScreen> with WidgetsBindingObserver {
  static const _channel = MethodChannel('com.flopster101.siliconplayer/playback');

  late Directory _currentDirectory;
  List<FileSystemEntity> _entities = [];
  bool _isLoading = false;
  String _errorMessage = '';
  bool _hasStoragePermission = false;

  // Dynamic set loaded from the C++ core engine via MethodChannel
  final Set<String> _supportedExtensions = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkAndInit();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // Recheck permissions when returning to the app
      _checkAndInit();
    }
  }

  Future<void> _checkAndInit() async {
    final granted = await _channel.invokeMethod<bool>('checkStoragePermission') ?? false;
    setState(() {
      _hasStoragePermission = granted;
    });

    if (granted) {
      if (_supportedExtensions.isEmpty) {
        await _loadSupportedExtensions();
      }
      _initDirectory();
      
      // Request notification permissions for background media playbacks on Android 13+
      await _channel.invokeMethod('requestNotificationPermission');
    }
  }

  Future<void> _requestPermission() async {
    await _channel.invokeMethod('requestStoragePermission');
  }

  Future<void> _loadSupportedExtensions() async {
    try {
      final List<dynamic>? extensions = await _channel.invokeMethod<List<dynamic>>('getSupportedExtensions');
      if (extensions != null) {
        setState(() {
          _supportedExtensions.addAll(extensions.map((e) => e.toString().toLowerCase()));
        });
        debugPrint('Loaded ${_supportedExtensions.length} supported extensions from C++ engine');
      }
    } catch (e) {
      debugPrint('Failed to load supported extensions: $e');
      setState(() {
        _supportedExtensions.addAll({
          'mod', 'xm', 'it', 's3m', 'sid', 'vgm', 'vgz', 'nsf',
          'gbs', 'spc', 'flac', 'mp3', 'wav', 'ogg', 'med', 'okt',
          'ahx', 'hvl', 'dmf', 'prg'
        });
      });
    }
  }

  void _initDirectory() {
    String startPath = '/storage/emulated/0';
    if (Platform.isAndroid) {
      final dir = Directory(startPath);
      if (!dir.existsSync()) {
        startPath = '/sdcard';
      }
    } else {
      startPath = Platform.environment['HOME'] ?? Directory.current.path;
    }
    
    _navigateTo(Directory(startPath));
  }

  Future<void> _navigateTo(Directory directory) async {
    setState(() {
      _isLoading = true;
      _errorMessage = '';
    });

    try {
      if (await directory.exists()) {
        final list = await directory.list().toList();
        
        // Sort: directories first, then files (alphabetically)
        list.sort((a, b) {
          final aIsDir = a is Directory;
          final bIsDir = b is Directory;
          if (aIsDir && !bIsDir) return -1;
          if (!aIsDir && bIsDir) return 1;
          return p.basename(a.path).toLowerCase().compareTo(p.basename(b.path).toLowerCase());
        });

        setState(() {
          _currentDirectory = directory;
          _entities = list.where((entity) {
            if (entity is Directory) return true;
            if (entity is File) {
              return FileExtensionHeuristics.fileMatchesSupportedExtensions(
                entity.path,
                _supportedExtensions,
              );
            }
            return false;
          }).toList();
          _isLoading = false;
        });
      } else {
        setState(() {
          _errorMessage = 'Directory does not exist: ${directory.path}';
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = 'Permission Denied or Error loading directory:\n$e';
        _isLoading = false;
      });
    }
  }

  void _goUp() {
    final parent = _currentDirectory.parent;
    if (parent.path != _currentDirectory.path) {
      _navigateTo(parent);
    }
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (!_hasStoragePermission) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('File Browser', style: TextStyle(fontWeight: FontWeight.bold)),
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Card(
              elevation: 4,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircleAvatar(
                      radius: 36,
                      backgroundColor: theme.colorScheme.primaryContainer,
                      foregroundColor: theme.colorScheme.onPrimaryContainer,
                      child: const Icon(Icons.info, size: 36),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      'Storage access required',
                      style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Grant file access so Silicon Player can scan and play your audio library.',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 24),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      onPressed: _requestPermission,
                      child: const Text('Grant permission'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    }

    final isRoot = _currentDirectory.path == _currentDirectory.parent.path;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'File Browser',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            Text(
              _currentDirectory.path,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              overflow: TextOverflow.fade,
              maxLines: 1,
              softWrap: false,
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.home),
            tooltip: 'Go to Home Directory',
            onPressed: _initDirectory,
          ),
        ],
      ),
      body: Column(
        children: [
          // Path breadcrumbs navigation header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: theme.colorScheme.surfaceContainerLow,
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_upward),
                  tooltip: 'Go up a directory',
                  onPressed: isRoot ? null : _goUp,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    p.basename(_currentDirectory.path).isEmpty 
                        ? 'Root' 
                        : p.basename(_currentDirectory.path),
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
          
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage.isNotEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.error_outline,
                                color: theme.colorScheme.error,
                                size: 48,
                              ),
                              const SizedBox(height: 16),
                              Text(
                                _errorMessage,
                                textAlign: TextAlign.center,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  color: theme.colorScheme.error,
                                ),
                              ),
                              const SizedBox(height: 24),
                              ElevatedButton.icon(
                                onPressed: _initDirectory,
                                icon: const Icon(Icons.refresh),
                                label: const Text('Reset to Start'),
                              ),
                            ],
                          ),
                        ),
                      )
                    : _entities.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(
                                  Icons.folder_open,
                                  color: theme.colorScheme.onSurfaceVariant.withOpacity(0.5),
                                  size: 48,
                                ),
                                const SizedBox(height: 16),
                                Text(
                                  'No supported audio files found',
                                  style: theme.textTheme.bodyLarge?.copyWith(
                                    color: theme.colorScheme.onSurfaceVariant,
                                  ),
                                ),
                              ],
                            ),
                          )
                        : ListView.builder(
                            itemCount: _entities.length,
                            itemBuilder: (context, index) {
                              final entity = _entities[index];
                              final isDir = entity is Directory;
                              final name = p.basename(entity.path);
                              final isPlaying = widget.currentPlayingPath == entity.path;

                              final cleanTitle = isDir 
                                  ? name 
                                  : FileExtensionHeuristics.inferredDisplayTitleForName(name);
                              
                              final primaryExt = isDir
                                  ? ''
                                  : FileExtensionHeuristics.inferredPrimaryExtensionForName(entity.path) ?? '';
                              
                              final badgeColor = isDir
                                  ? theme.colorScheme.primary
                                  : FileExtensionHeuristics.getExtensionColor(primaryExt, theme);

                              final leadingIcon = isDir
                                  ? Icons.folder
                                  : FileExtensionHeuristics.getExtensionIcon(primaryExt);

                              return ListTile(
                                leading: CircleAvatar(
                                  backgroundColor: isDir
                                      ? theme.colorScheme.primaryContainer
                                      : isPlaying
                                          ? theme.colorScheme.tertiaryContainer
                                          : badgeColor.withOpacity(0.15),
                                  foregroundColor: isDir
                                      ? theme.colorScheme.onPrimaryContainer
                                      : isPlaying
                                          ? theme.colorScheme.onTertiaryContainer
                                          : badgeColor,
                                  child: Icon(leadingIcon),
                                ),
                                title: Text(
                                  cleanTitle,
                                  style: TextStyle(
                                    fontWeight: isDir ? FontWeight.bold : FontWeight.normal,
                                    color: isPlaying ? theme.colorScheme.primary : null,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                                subtitle: isDir
                                    ? null
                                    : FutureBuilder<FileStat>(
                                        future: entity.stat(),
                                        builder: (context, snapshot) {
                                          if (snapshot.hasData) {
                                            final stat = snapshot.data!;
                                            return Text('${primaryExt.toUpperCase()} • ${_formatSize(stat.size)}');
                                          }
                                          return const Text('Loading...');
                                        },
                                      ),
                                trailing: isPlaying
                                    ? Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                        decoration: BoxDecoration(
                                          color: theme.colorScheme.primary.withOpacity(0.2),
                                          borderRadius: BorderRadius.circular(12),
                                        ),
                                        child: Text(
                                          'PLAYING',
                                          style: TextStyle(
                                            color: theme.colorScheme.primary,
                                            fontSize: 10,
                                            fontWeight: FontWeight.bold,
                                          ),
                                        ),
                                      )
                                    : const Icon(Icons.chevron_right),
                                onTap: () {
                                  if (isDir) {
                                    _navigateTo(entity);
                                  } else {
                                    widget.onFileSelected(entity.path);
                                  }
                                },
                              );
                            },
                          ),
          ),
        ],
      ),
    );
  }
}
