import 'package:drift/drift.dart';

/// 分片上传状态表。
///
/// status: pending | uploading | completed | failed | cancelled
@DataClassName('TaskChunkEntity')
@TableIndex(name: 'idx_chunks_task', columns: {#taskId})
class TaskChunks extends Table {
  TextColumn get id => text()();
  TextColumn get taskId => text()();
  IntColumn get chunkIndex => integer()();
  IntColumn get offset => integer()();
  IntColumn get length => integer()();
  TextColumn get status => text().withDefault(const Constant('pending'))();
  TextColumn get etagOrToken => text().nullable()();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column<Object>> get primaryKey => {id};

  @override
  List<Set<Column<Object>>> get uniqueKeys => [
        {taskId, chunkIndex},
      ];
}
