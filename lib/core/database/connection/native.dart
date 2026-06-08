import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqlite3_flutter_libs/sqlite3_flutter_libs.dart';

/// 主 isolate 端的 Drift 连接 —— 用于 UI 端读取。
///
/// 实际写入由后台 service isolate 完成。两端通过同一文件协作；
/// 跨进程并发由 SQLite 的 WAL 模式保证。
QueryExecutor openConnection() {
  return LazyDatabase(() async {
    final dir = await getApplicationDocumentsDirectory();
    final file = File(p.join(dir.path, 'nastools.db'));

    if (Platform.isAndroid) {
      await applyWorkaroundToOpenSqlite3OnOldAndroidVersions();
    }

    return NativeDatabase.createInBackground(
      file,
      logStatements: false,
    );
  });
}

/// 给 service isolate 用：拿到 db 文件路径后自己 open（无 path_provider 依赖）。
Future<String> resolveDbPath() async {
  final dir = await getApplicationDocumentsDirectory();
  return p.join(dir.path, 'nastools.db');
}
