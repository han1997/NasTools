import '../domain/upload_chunk.dart';
import 'local_source.dart';

/// 递归枚举 [root] 下的所有文件 —— 不预先把文件列表存数据库，
/// 而是边遍历边产出，避免巨型文件夹一次性占内存。
class FolderWalker {
  FolderWalker(this.source);
  final LocalSource source;

  /// 深度优先遍历，产生 [LocalFileEntry]。
  ///
  /// [filterRegex] 非空时，仅产出 basename 命中正则的文件。目录不参与正则匹配，
  /// 始终递归进入（否则会丢掉里面可能命中的文件）。无效正则被视为不过滤。
  Stream<LocalFileEntry> walk(String rootUri, {String? filterRegex}) async* {
    final pattern = _compile(filterRegex);
    final stack = <_StackFrame>[_StackFrame(rootUri, '')];

    while (stack.isNotEmpty) {
      final frame = stack.removeLast();
      final children = await source.listChildren(frame.uri);
      for (final c in children) {
        final relative = frame.prefix.isEmpty ? c.name : '${frame.prefix}/${c.name}';
        if (c.isDirectory) {
          stack.add(_StackFrame(c.uri, relative));
        } else {
          if (pattern != null && !pattern.hasMatch(c.name)) continue;
          yield LocalFileEntry(
            uri: c.uri,
            relativePath: relative,
            size: c.size,
            modifiedAt: c.modifiedAt,
          );
        }
      }
    }
  }

  /// 先快速统计文件数与总字节（不读取内容，只列目录）。
  Future<({int filesTotal, int bytesTotal})> summary(
    String rootUri, {
    String? filterRegex,
  }) async {
    var files = 0;
    var bytes = 0;
    await for (final e in walk(rootUri, filterRegex: filterRegex)) {
      files++;
      bytes += e.size;
    }
    return (filesTotal: files, bytesTotal: bytes);
  }

  static RegExp? _compile(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      return RegExp(raw);
    } catch (_) {
      // 无效正则视为不过滤；executor 仍会继续上传所有文件。
      return null;
    }
  }
}

class _StackFrame {
  _StackFrame(this.uri, this.prefix);
  final String uri;
  final String prefix;
}
