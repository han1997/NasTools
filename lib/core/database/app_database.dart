import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

import 'connection/native.dart';
import 'daos/log_dao.dart';
import 'daos/nas_config_dao.dart';
import 'daos/task_chunk_dao.dart';
import 'daos/task_dao.dart';
import 'daos/upload_preset_dao.dart';
import 'tables/file_index.dart';
import 'tables/logs.dart';
import 'tables/nas_configs.dart';
import 'tables/task_chunks.dart';
import 'tables/tasks.dart';
import 'tables/upload_presets.dart';

part 'app_database.g.dart';

@DriftDatabase(
  tables: [NasConfigs, Tasks, TaskChunks, FileIndex, Logs, UploadPresets],
  daos: [NasConfigDao, TaskDao, TaskChunkDao, LogDao, UploadPresetDao],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(openConnection());
  AppDatabase.forExecutor(QueryExecutor e) : super(e);

  /// schemaVersion 历史：
  /// * 1 —— 初始（nas_configs / tasks / task_chunks / file_index / logs）
  /// * 2 —— 加 upload_presets
  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) => m.createAll(),
        onUpgrade: (m, from, to) async {
          if (from < 2) {
            await m.createTable(uploadPresets);
          }
        },
      );

  /// 简易 uuid 生成入口，给 DAO 用。
  String newId() => const Uuid().v4();
}
