import 'package:drift/drift.dart';

import '../app_database.dart';
import '../tables/task_chunks.dart';

part 'task_chunk_dao.g.dart';

@DriftAccessor(tables: [TaskChunks])
class TaskChunkDao extends DatabaseAccessor<AppDatabase> with _$TaskChunkDaoMixin {
  TaskChunkDao(super.db);

  Future<List<TaskChunkEntity>> getByTask(String taskId) {
    return (select(taskChunks)
          ..where((t) => t.taskId.equals(taskId))
          ..orderBy([(t) => OrderingTerm.asc(t.chunkIndex)]))
        .get();
  }

  Future<void> insertAll(List<TaskChunksCompanion> entries) async {
    await batch((b) => b.insertAll(taskChunks, entries));
  }

  Future<int> updateStatus(
    String id,
    String status, {
    String? etagOrToken,
  }) {
    return (update(taskChunks)..where((t) => t.id.equals(id))).write(
      TaskChunksCompanion(
        status: Value(status),
        etagOrToken: etagOrToken != null ? Value(etagOrToken) : const Value.absent(),
        updatedAt: Value(DateTime.now()),
      ),
    );
  }

  Future<int> deleteByTask(String taskId) =>
      (delete(taskChunks)..where((t) => t.taskId.equals(taskId))).go();
}
