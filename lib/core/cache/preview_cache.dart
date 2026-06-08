import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// 预览缓存目录管理。
///
/// 目录结构：`{getTemporaryDirectory()}/preview/<configId>/<sha1(path)[..16]><ext>`。
/// 每个 NAS 配置一个独立子目录，便于按配置清理（暂未启用）。
///
/// v1 不做 LRU；用户主动从设置页一键清理。
class PreviewCache {
  PreviewCache._();

  /// 拼出某个远端文件对应的本地缓存路径。
  static Future<File> fileFor({
    required String configId,
    required String remotePath,
    required String extension,
  }) async {
    final tmp = await getTemporaryDirectory();
    final hash = sha1.convert(remotePath.codeUnits).toString().substring(0, 16);
    final dir = Directory(p.join(tmp.path, 'preview', configId));
    if (!await dir.exists()) await dir.create(recursive: true);
    return File(p.join(dir.path, '$hash$extension'));
  }

  /// 整个 preview/ 子树清理。返回（清掉的字节数, 文件数）的粗略统计。
  static Future<({int bytes, int files})> clearAll() async {
    final tmp = await getTemporaryDirectory();
    final root = Directory(p.join(tmp.path, 'preview'));
    if (!await root.exists()) return (bytes: 0, files: 0);
    var bytes = 0;
    var files = 0;
    await for (final e in root.list(recursive: true)) {
      if (e is File) {
        try {
          bytes += await e.length();
          files++;
        } catch (_) {}
      }
    }
    try {
      await root.delete(recursive: true);
    } catch (_) {}
    return (bytes: bytes, files: files);
  }
}
