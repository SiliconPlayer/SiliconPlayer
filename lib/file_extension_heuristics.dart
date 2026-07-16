import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;

class FileExtensionHeuristics {
  static bool isLikelyExtensionToken(String token) {
    if (token.isEmpty || token.trim().isEmpty || token.length > 16) return false;
    final regex = RegExp(r'^[a-zA-Z0-9\+\-_]+$');
    return regex.hasMatch(token);
  }

  static List<String> extensionCandidatesForName(String name) {
    final baseName = p.basename(name).trim();
    if (baseName.isEmpty) return [];

    final candidates = <String>{};
    final firstDot = baseName.indexOf('.');
    final lastDot = baseName.lastIndexOf('.');

    // 1. Suffix match (e.g. song.mod)
    if (lastDot > 0 && lastDot < baseName.length - 1) {
      final suffix = baseName.substring(lastDot + 1).trim();
      if (isLikelyExtensionToken(suffix)) {
        candidates.add(suffix.toLowerCase());
      }
    }

    // 2. Middle match (e.g. song.mod.gz)
    if (lastDot > 0) {
      final secondLastDot = baseName.lastIndexOf('.', lastDot - 1);
      if (secondLastDot >= 0 && secondLastDot < lastDot) {
        final middle = baseName.substring(secondLastDot + 1, lastDot).trim();
        if (isLikelyExtensionToken(middle)) {
          candidates.add(middle.toLowerCase());
        }
      }
    }

    // 3. Prefix match (e.g. mod.song)
    if (firstDot > 0) {
      final prefix = baseName.substring(0, firstDot).trim();
      if (isLikelyExtensionToken(prefix)) {
        candidates.add(prefix.toLowerCase());
      }
    }

    return candidates.toList();
  }

  static String? inferredPrimaryExtensionForName(String name) {
    final candidates = extensionCandidatesForName(name);
    return candidates.isNotEmpty ? candidates.first : null;
  }

  static bool fileMatchesSupportedExtensions(String filename, Set<String> supportedExtensions) {
    if (supportedExtensions.isEmpty) return false;
    final candidates = extensionCandidatesForName(filename);
    for (final candidate in candidates) {
      if (supportedExtensions.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  static String inferredDisplayTitleForName(String name) {
    final baseName = p.basename(name).trim();
    if (baseName.isEmpty) return name;

    final firstDot = baseName.indexOf('.');
    if (firstDot > 0 && firstDot < baseName.length - 1) {
      final prefix = baseName.substring(0, firstDot).trim();
      final remainder = baseName.substring(firstDot + 1).trim();
      if (isLikelyExtensionToken(prefix) && remainder.isNotEmpty && !isLikelyExtensionToken(remainder)) {
        final remainderLastDot = remainder.lastIndexOf('.');
        if (remainderLastDot > 0) {
          return remainder.substring(0, remainderLastDot).isEmpty ? remainder : remainder.substring(0, remainderLastDot);
        } else {
          return remainder;
        }
      }
    }

    final lastDot = baseName.lastIndexOf('.');
    if (lastDot > 0) {
      return baseName.substring(0, lastDot).isEmpty ? baseName : baseName.substring(0, lastDot);
    }
    return baseName;
  }

  // Visual categorization and color maps for tracker/chip format badges
  static Color getExtensionColor(String ext, ThemeData theme) {
    final cleanExt = ext.toLowerCase();
    
    // Tracker modules
    if (const {'mod', 'xm', 'it', 's3m', 'med', 'okt', 'mtm', 'stm'}.contains(cleanExt)) {
      return const Color(0xFF00E676); // Neon Green
    }
    // C64 SID
    if (const {'sid', 'prg', 'dmp', 'mus'}.contains(cleanExt)) {
      return const Color(0xFFFF9100); // Amber Orange
    }
    // VGM Arcade/Sega
    if (const {'vgm', 'vgz', 'sgc', 'gym'}.contains(cleanExt)) {
      return const Color(0xFF00E5FF); // Cyan
    }
    // Console Chiptune
    if (const {'nsf', 'nsfe', 'gbs', 'spc', 'hes', 'kss'}.contains(cleanExt)) {
      return const Color(0xFFFF1744); // Red
    }
    // FM Synth
    if (const {'imf', 'la', 'rol', 'amd', 'hsc', 'msc', 'rad', 'sng'}.contains(cleanExt)) {
      return const Color(0xFFFFEA00); // Yellow
    }
    // Amiga / UADE
    if (const {'ahx', 'hvl', 'bp', 'fc14', 'tfmx', 'soundfx'}.contains(cleanExt)) {
      return const Color(0xFFD500F9); // Magenta/Purple
    }
    // Digital Audio
    if (const {'mp3', 'flac', 'wav', 'ogg', 'm4a', 'aac'}.contains(cleanExt)) {
      return const Color(0xFF2979FF); // Blue
    }
    return theme.colorScheme.onSurfaceVariant;
  }

  static IconData getExtensionIcon(String ext) {
    final cleanExt = ext.toLowerCase();
    
    if (const {'mod', 'xm', 'it', 's3m', 'med', 'okt'}.contains(cleanExt)) {
      return Icons.piano;
    }
    if (const {'sid', 'prg'}.contains(cleanExt)) {
      return Icons.developer_board;
    }
    if (const {'vgm', 'vgz'}.contains(cleanExt)) {
      return Icons.videogame_asset;
    }
    if (const {'nsf', 'gbs', 'spc'}.contains(cleanExt)) {
      return Icons.gamepad;
    }
    if (const {'imf', 'la', 'rol'}.contains(cleanExt)) {
      return Icons.tune;
    }
    if (const {'ahx', 'hvl', 'bp'}.contains(cleanExt)) {
      return Icons.memory;
    }
    return Icons.audiotrack;
  }
}
