import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/isolate.dart';
import 'package:drift/native.dart';

import 'native.dart' show resolveDbPath;

/// Service isolate 端：自己打开 db 文件（不依赖 path_provider）。
///
/// 主 isolate 通过 [spawnBackgroundIsolate] 把 db 路径传过来，由
/// background isolate 持有真正的 [NativeDatabase]，主 isolate 通过
/// [DriftIsolate.connect] 拿到代理连接。
QueryExecutor openDbForPath(String path) {
  return NativeDatabase(File(path), logStatements: false);
}

/// 主 isolate 调用：起一个 [DriftIsolate]，返回供
/// [DatabaseConnection.fromConnection] 使用的连接。
Future<DriftIsolate> spawnDriftIsolate() async {
  final path = await resolveDbPath();
  return DriftIsolate.spawn(() => _BackgroundEntry(path).connect());
}

class _BackgroundEntry {
  _BackgroundEntry(this.path);
  final String path;

  DatabaseConnection connect() {
    final db = openDbForPath(path);
    return DatabaseConnection(db);
  }
}
