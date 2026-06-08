import 'package:drift/drift.dart';

/// 日志表。启动时清理 7 天前的记录。
///
/// level: trace | debug | info | warn | error
@DataClassName('LogEntity')
@TableIndex(name: 'idx_logs_ts', columns: {#ts})
@TableIndex(name: 'idx_logs_task', columns: {#taskId})
class Logs extends Table {
  IntColumn get id => integer().autoIncrement()();
  DateTimeColumn get ts => dateTime().withDefault(currentDateAndTime)();
  TextColumn get level => text()();
  TextColumn get tag => text()();
  TextColumn get message => text()();
  TextColumn get taskId => text().nullable()();
}
