import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../adapters/adapter_factory.dart';
import 'database/app_database.dart';
import 'logger/app_logger.dart';
import 'logger/drift_log_sink.dart';
import 'network/dio_factory.dart';
import 'settings/settings_repository.dart';
import 'task/task_manager.dart';

/// L0 —— 单例资源。
///
/// 这些 provider 在 main isolate 和 service isolate 都需要；service isolate
/// 端会有平行的 [ProviderContainer] 重新创建实例（数据库连接共用 sqlite 文件）。

final appDatabaseProvider = Provider<AppDatabase>((ref) {
  final db = AppDatabase();
  ref.onDispose(() => db.close());
  return db;
});

final sharedPreferencesProvider = FutureProvider<SharedPreferences>((ref) async {
  return SharedPreferences.getInstance();
});

final settingsRepositoryProvider = Provider<SettingsRepository>((ref) {
  final prefs = ref.watch(sharedPreferencesProvider).maybeWhen(
        data: (v) => v,
        orElse: () => throw StateError('SettingsRepository accessed before SharedPreferences ready'),
      );
  return SettingsRepository(prefs);
});

final appLoggerProvider = Provider<AppLogger>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final logger = AppLogger(sink: DriftLogSink(db.logDao));
  return logger;
});

final dioFactoryProvider = Provider<DioFactory>((ref) {
  final f = DioFactory();
  ref.onDispose(f.disposeAll);
  return f;
});

final adapterFactoryProvider = Provider<AdapterFactory>((ref) {
  return AdapterFactory(ref.watch(dioFactoryProvider));
});

final taskManagerProvider = Provider<TaskManager>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final log = ref.watch(appLoggerProvider);
  final tm = TaskManager(database: db, logger: log);
  ref.onDispose(() => tm.stop());
  return tm;
});

/// 上传预设 DAO（绑定 appDatabase，进程内单例）。
final uploadPresetDaoProvider = Provider((ref) {
  return ref.watch(appDatabaseProvider).uploadPresetDao;
});
