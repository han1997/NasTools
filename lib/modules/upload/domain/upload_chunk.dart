import 'package:meta/meta.dart';

/// 本地文件源条目 —— folder walker 产出。
@immutable
class LocalFileEntry {
  const LocalFileEntry({
    required this.uri,
    required this.relativePath,
    required this.size,
    this.modifiedAt,
  });

  final String uri;
  final String relativePath; // 相对 root 的路径（含文件名）
  final int size;
  final DateTime? modifiedAt;
}
