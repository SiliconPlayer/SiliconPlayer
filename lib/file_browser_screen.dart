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
  State<FileBrowserScreen> createState() => FileBrowserScreenState();
}

class FileBrowserScreenState extends State<FileBrowserScreen> with WidgetsBindingObserver {
  static const _channel = MethodChannel('com.flopster101.siliconplayer/playback');

  late Directory _currentDirectory;
  List<FileSystemEntity> _entities = [];
  bool _isLoading = false;
  String _errorMessage = '';
  bool _hasStoragePermission = false;

  // Cache for folder summary count to prevent recalculating on scroll
  final Map<String, String> _folderSummaryCache = {};

  // Storage locations list
  final List<String> _storageLocations = [];

  // Dynamic set loaded from the C++ core engine via MethodChannel
  final Set<String> _supportedExtensions = {};

  bool _isInitialized = false;
  final List<String> _navigationHistory = [];
  bool _isSearching = false;
  String _searchQuery = '';

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
      await _detectStorageLocations();
      _initDirectory();
      
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
      }
    } catch (e) {
      setState(() {
        _supportedExtensions.addAll({
          'mod', 'xm', 'it', 's3m', 'sid', 'vgm', 'vgz', 'nsf',
          'gbs', 'spc', 'flac', 'mp3', 'wav', 'ogg', 'med', 'okt',
          'ahx', 'hvl', 'dmf', 'prg'
        });
      });
    }
  }

  Future<void> _detectStorageLocations() async {
    final List<String> detected = [];
    
    if (Platform.isAndroid) {
      detected.add('/storage/emulated/0');
      detected.add('/');
      try {
        final storageDir = Directory('/storage');
        if (await storageDir.exists()) {
          final list = await storageDir.list().toList();
          for (final entity in list) {
            if (entity is Directory) {
              final name = p.basename(entity.path);
              if (name != 'self' && name != 'emulated' && name != 'knox' && !name.startsWith('.')) {
                if (await entity.exists()) {
                  detected.add(entity.path);
                }
              }
            }
          }
        }
      } catch (_) {}
    } else {
      detected.add(Platform.environment['HOME'] ?? Directory.current.path);
      detected.add('/');
    }

    final unique = detected.toSet().toList();
    setState(() {
      _storageLocations.clear();
      _storageLocations.addAll(unique);
    });
  }

  String _getHomePath() {
    if (Platform.isAndroid) {
      final dir = Directory('/storage/emulated/0');
      if (dir.existsSync()) return '/storage/emulated/0';
      return '/sdcard';
    }
    return Platform.environment['HOME'] ?? Directory.current.path;
  }

  void _initDirectory() {
    _navigationHistory.clear();
    _navigateTo(Directory(_getHomePath()));
  }

  Future<void> _navigateTo(Directory directory, {bool isBack = false}) async {
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

        if (_isInitialized && !isBack && _currentDirectory.path != directory.path) {
          _navigationHistory.add(_currentDirectory.path);
        }

        setState(() {
          _currentDirectory = directory;
          _isInitialized = true;
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

  Future<bool> handleBackPress() async {
    if (_isSearching) {
      setState(() {
        _isSearching = false;
        _searchQuery = '';
      });
      return true;
    }
    
    if (_navigationHistory.isNotEmpty) {
      final prevPath = _navigationHistory.removeLast();
      await _navigateTo(Directory(prevPath), isBack: true);
      return true;
    }
    
    final homePath = _getHomePath();
    if (_currentDirectory.path != homePath) {
      await _navigateTo(Directory(homePath));
      return true;
    }
    
    return false;
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

  String _getStorageLabel(String path) {
    if (path == '/storage/emulated/0') return 'Internal Storage';
    if (path == '/') return 'System Root';
    if (path.startsWith('/storage/')) {
      final name = path.substring(9);
      return 'SD Card / USB ($name)';
    }
    return p.basename(path).isEmpty ? path : p.basename(path);
  }

  IconData _getStorageIcon(String path) {
    if (path == '/storage/emulated/0') return Icons.phone_android;
    if (path == '/') return Icons.folder_copy;
    if (path.startsWith('/storage/')) return Icons.sd_card;
    return Icons.home;
  }

  // Fast asynchronous folder children counter
  Future<String> _getFolderSummary(Directory dir) async {
    final cacheKey = dir.path;
    if (_folderSummaryCache.containsKey(cacheKey)) {
      return _folderSummaryCache[cacheKey]!;
    }

    try {
      int filesCount = 0;
      int foldersCount = 0;
      final children = await dir.list().toList();
      for (final child in children) {
        if (child is Directory) {
          foldersCount++;
        } else if (child is File) {
          final ext = p.extension(child.path).replaceAll('.', '').toLowerCase();
          // Only count supported files
          if (_supportedExtensions.contains(ext) || 
              FileExtensionHeuristics.fileMatchesSupportedExtensions(child.path, _supportedExtensions)) {
            filesCount++;
          }
        }
      }

      String summary = '';
      if (foldersCount == 0) {
        summary = '$filesCount file${filesCount == 1 ? "" : "s"}';
      } else if (filesCount == 0) {
        summary = '$foldersCount folder${foldersCount == 1 ? "" : "s"}';
      } else {
        summary = '$foldersCount folder${foldersCount == 1 ? "" : "s"} • $filesCount file${filesCount == 1 ? "" : "s"}';
      }

      _folderSummaryCache[cacheKey] = summary;
      return summary;
    } catch (_) {
      return '0 files';
    }
  }

  Widget _buildBodyContent(ThemeData theme, List<FileSystemEntity?> displayList) {
    if (!_isInitialized || _isLoading) {
      return const Center(
        key: ValueKey('loading'),
        child: CircularProgressIndicator(),
      );
    }
    
    if (_errorMessage.isNotEmpty) {
      return Center(
        key: const ValueKey('error'),
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
      );
    }
    
    final filteredList = displayList.where((entity) {
      if (entity == null) return true; // keep parent directory
      if (_searchQuery.isEmpty) return true;
      final name = p.basename(entity.path);
      return name.toLowerCase().contains(_searchQuery.toLowerCase());
    }).toList();

    if (filteredList.isEmpty || (filteredList.length == 1 && filteredList.first == null)) {
      return RefreshIndicator(
        key: const ValueKey('empty'),
        onRefresh: () => _navigateTo(_currentDirectory),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(height: MediaQuery.of(context).size.height * 0.25),
            Icon(
              Icons.folder_open,
              color: theme.colorScheme.onSurfaceVariant.withOpacity(0.5),
              size: 48,
            ),
            const SizedBox(height: 16),
            Center(
              child: Text(
                _searchQuery.isNotEmpty ? 'No matches found' : 'No supported audio files found',
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      key: ValueKey<String>('${_currentDirectory.path}_${_searchQuery}'),
      onRefresh: () => _navigateTo(_currentDirectory),
      child: ListView.builder(
        itemCount: filteredList.length,
        itemBuilder: (context, index) {
          final entity = filteredList[index];

          // Render ".." parent folder entry
          if (entity == null) {
            return ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
              leading: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary.withOpacity(0.85),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.folder, color: Colors.white),
              ),
              title: const Text(
                '..',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              subtitle: const Text(
                'Parent directory',
                style: TextStyle(fontSize: 13, color: Colors.grey),
              ),
              onTap: _goUp,
            );
          }

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
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
            leading: Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: isDir
                    ? theme.colorScheme.primary.withOpacity(0.85)
                    : theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(
                leadingIcon,
                color: isDir 
                    ? Colors.white 
                    : isPlaying 
                        ? theme.colorScheme.primary 
                        : badgeColor,
              ),
            ),
            title: Text(
              cleanTitle,
              style: TextStyle(
                fontWeight: isDir ? FontWeight.bold : FontWeight.normal,
                fontSize: 16,
                color: isPlaying ? theme.colorScheme.primary : null,
              ),
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: isDir
                ? FutureBuilder<String>(
                    future: _getFolderSummary(entity),
                    builder: (context, snapshot) {
                      return Text(
                        snapshot.data ?? 'Loading...',
                        style: const TextStyle(fontSize: 13, color: Colors.grey),
                      );
                    },
                  )
                : FutureBuilder<FileStat>(
                    future: entity.stat(),
                    builder: (context, snapshot) {
                      if (snapshot.hasData) {
                        final stat = snapshot.data!;
                        return Text(
                          '${primaryExt.toUpperCase()} • ${_formatSize(stat.size)}',
                          style: const TextStyle(fontSize: 13, color: Colors.grey),
                        );
                      }
                      return const Text('Loading...', style: TextStyle(fontSize: 13, color: Colors.grey));
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
                : isDir
                    ? const Icon(Icons.chevron_right, size: 20)
                    : null,
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
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (!_hasStoragePermission) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Silicon Player', style: TextStyle(fontWeight: FontWeight.bold)),
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

    final isRoot = _isInitialized && _currentDirectory.path == _currentDirectory.parent.path;

    // Build the list of entities, injecting the parent directory ".." if not at root
    final List<FileSystemEntity?> displayList = [];
    if (!isRoot && _isInitialized) {
      displayList.add(null); // null acts as placeholder for the ".." entry
    }
    if (_isInitialized) {
      displayList.addAll(_entities);
    }

    return Scaffold(
      appBar: _isSearching
          ? AppBar(
              leading: IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () {
                  setState(() {
                    _isSearching = false;
                    _searchQuery = '';
                  });
                },
              ),
              title: TextField(
                autofocus: true,
                style: TextStyle(color: theme.colorScheme.onSurface),
                decoration: const InputDecoration(
                  hintText: 'Search files & folders...',
                  border: InputBorder.none,
                ),
                onChanged: (val) {
                  setState(() {
                    _searchQuery = val;
                  });
                },
              ),
              actions: [
                if (_searchQuery.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.clear),
                    onPressed: () {
                      setState(() {
                        _searchQuery = '';
                      });
                    },
                  ),
              ],
            )
          : AppBar(
              title: const Text(
                'Silicon Player',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              actions: [
                IconButton(
                  icon: const Icon(Icons.home),
                  tooltip: 'Home Directory',
                  onPressed: _initDirectory,
                ),
                IconButton(
                  icon: const Icon(Icons.link),
                  tooltip: 'Network Link',
                  onPressed: () {},
                ),
                IconButton(
                  icon: const Icon(Icons.settings),
                  tooltip: 'Settings',
                  onPressed: () {},
                ),
              ],
            ),
      body: Column(
        children: [
          // Sub-bar exactly matching Compose layout in Screenshot 2!
          if (_isInitialized && !_isSearching)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              color: theme.colorScheme.surfaceContainerLow,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  // Left side clickable storage dropdown (Huge hitbox!)
                  Expanded(
                    child: Theme(
                      data: theme.copyWith(cardColor: theme.colorScheme.surfaceContainer),
                      child: PopupMenuButton<String>(
                        offset: const Offset(0, 40),
                        tooltip: 'Select storage location',
                        onSelected: (path) {
                          _navigateTo(Directory(path));
                        },
                        itemBuilder: (context) {
                          return _storageLocations.map((path) {
                            return PopupMenuItem<String>(
                              value: path,
                              child: Row(
                                children: [
                                  Icon(_getStorageIcon(path), size: 20, color: theme.colorScheme.primary),
                                  const SizedBox(width: 12),
                                  Text(_getStorageLabel(path)),
                                ],
                              ),
                            );
                          }).toList();
                        },
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(
                                  'File Browser',
                                  style: theme.textTheme.bodyLarge?.copyWith(
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                                const SizedBox(width: 4),
                                const Icon(Icons.arrow_drop_down, size: 20),
                              ],
                            ),
                            const SizedBox(height: 2),
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  _getStorageIcon(_currentDirectory.path),
                                  size: 14,
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                                const SizedBox(width: 4),
                                Expanded(
                                  child: Text(
                                    _currentDirectory.path,
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                    ),
                                    overflow: TextOverflow.ellipsis,
                                    maxLines: 1,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  
                  // Right side: Search button
                  IconButton(
                    icon: const Icon(Icons.search),
                    tooltip: 'Search',
                    onPressed: () {
                      setState(() {
                        _isSearching = true;
                      });
                    },
                  ),
                ],
              ),
            ),
            
          Expanded(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 200),
              transitionBuilder: (child, animation) {
                return FadeTransition(
                  opacity: animation,
                  child: SlideTransition(
                    position: Tween<Offset>(
                      begin: const Offset(0.04, 0.0),
                      end: Offset.zero,
                    ).animate(CurvedAnimation(
                      parent: animation,
                      curve: Curves.easeOut,
                    )),
                    child: child,
                  ),
                );
              },
              child: _buildBodyContent(theme, displayList),
            ),
          ),
        ],
      ),
    );
  }
}
