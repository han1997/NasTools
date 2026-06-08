import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/services.dart';

/// 抽象本地源 —— upload 模块只依赖此接口，不关心 SAF 还是 File。
abstract class LocalSource {
  /// 列直接子节点。
  Future<List<LocalSourceEntry>> listChildren(String uri);

  /// 流式读取一段字节。
  Stream<List<int>> readChunked(String uri, {required int chunkSize, int offset = 0});

  /// 元信息。
  Future<LocalSourceEntry?> stat(String uri);

  /// 删除单个文件。返回 true 表示已不存在（成功 / 本来就不在），false
  /// 表示 provider 拒绝。调用方负责把 false 当作"未删"处理。
  Future<bool> delete(String uri);
}

class LocalSourceEntry {
  LocalSourceEntry({
    required this.uri,
    required this.name,
    required this.isDirectory,
    this.size = 0,
    this.modifiedAt,
  });

  final String uri;
  final String name;
  final bool isDirectory;
  final int size;
  final DateTime? modifiedAt;
}

/// 基于 Android SAF 的实现。通过 MethodChannel `nastools/saf` 与 Kotlin 通信。
///
/// 协议方法：
/// * `listChildren(uri) -> List<Map>`
/// * `stat(uri) -> Map?`
/// * `read(uri, offset, length) -> Uint8List`
class SafLocalSource implements LocalSource {
  SafLocalSource() : _ch = const MethodChannel('nastools/saf');
  final MethodChannel _ch;

  @override
  Future<List<LocalSourceEntry>> listChildren(String uri) async {
    final raw = await _ch.invokeMethod<List<dynamic>>('listChildren', {'uri': uri});
    if (raw == null) return const [];
    return raw.cast<Map<dynamic, dynamic>>().map(_decode).toList();
  }

  @override
  Future<LocalSourceEntry?> stat(String uri) async {
    final raw = await _ch.invokeMethod<Map<dynamic, dynamic>>('stat', {'uri': uri});
    if (raw == null) return null;
    return _decode(raw);
  }

  @override
  Stream<List<int>> readChunked(String uri, {required int chunkSize, int offset = 0}) async* {
    var pos = offset;
    while (true) {
      final Uint8List? bytes = await _ch.invokeMethod<Uint8List>('read', {
        'uri': uri,
        'offset': pos,
        'length': chunkSize,
      });
      if (bytes == null || bytes.isEmpty) return;
      yield bytes;
      pos += bytes.length;
      if (bytes.length < chunkSize) return; // EOF
    }
  }

  LocalSourceEntry _decode(Map<dynamic, dynamic> m) {
    return LocalSourceEntry(
      uri: m['uri'] as String,
      name: m['name'] as String,
      isDirectory: (m['isDirectory'] as bool?) ?? false,
      size: (m['size'] as num?)?.toInt() ?? 0,
      modifiedAt: m['mtime'] is int
          ? DateTime.fromMillisecondsSinceEpoch(m['mtime'] as int)
          : null,
    );
  }

  @override
  Future<bool> delete(String uri) async {
    final ok = await _ch.invokeMethod<bool>('delete', {'uri': uri});
    return ok ?? false;
  }
}
