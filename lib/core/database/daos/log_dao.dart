import 'package:drift/drift.dart';

import '../app_database.dart';
import '../tables/logs.dart';

part 'log_dao.g.dart';

@DriftAccessor(tables: [Logs])
class LogDao extends DatabaseAccessor<AppDatabase> with _$LogDaoMixin {
  LogDao(super.db);

  Future<void> append({
    required String level,
    required String tag,
    required String message,
    String? taskId,
  }) async {
    await into(logs).insert(LogsCompanion.insert(
      level: level,
      tag: tag,
      message: message,
      taskId: Value(taskId),
    ));
  }

  Stream<List<LogEntity>> watchRecent({int limit = 500}) {
    return (select(logs)
          ..orderBy([(t) => OrderingTerm.desc(t.ts)])
          ..limit(limit))
        .watch();
  }

  /// 启动时清理 7 天前日志。
  Future<int> purgeOlderThan(Duration age) {
    final cutoff = DateTime.now().subtract(age);
    return (delete(logs)..where((t) => t.ts.isSmallerThanValue(cutoff))).go();
  }
}
