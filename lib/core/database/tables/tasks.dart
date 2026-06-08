import 'package:drift/drift.dart';

/// 统一任务表。所有模块（upload/ssh/dedup/...）共用一张表。
///
/// 模块特化数据存 [payloadJson]，由各 module 的 executor 反序列化。
/// [status] 取值：waiting | running | paused | failed | completed | cancelled
@DataClassName('TaskEntity')
@TableIndex(name: 'idx_tasks_status', columns: {#status})
@TableIndex(name: 'idx_tasks_module_type', columns: {#moduleId, #type})
class Tasks extends Table {
  TextColumn get id => text()();
  TextColumn get moduleId => text()(); // 'upload', 'ssh', ...
  TextColumn get type => text()(); // 模块内部细分（如 'folder_upload'）
  TextColumn get nasConfigId => text().nullable()();
  TextColumn get status => text().withDefault(const Constant('waiting'))();
  IntColumn get progressBytes => integer().withDefault(const Constant(0))();
  IntColumn get totalBytes => integer().withDefault(const Constant(0))();
  TextColumn get title => text().withDefault(const Constant(''))();
  TextColumn get payloadJson => text().withDefault(const Constant('{}'))();
  TextColumn get errorMessage => text().nullable()();
  IntColumn get retryCount => integer().withDefault(const Constant(0))();
  IntColumn get priority => integer().withDefault(const Constant(0))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column<Object>> get primaryKey => {id};
}
