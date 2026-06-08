import 'package:drift/drift.dart';

import '../../task/task_state.dart';
import '../app_database.dart';
import '../tables/tasks.dart';

part 'task_dao.g.dart';

@DriftAccessor(tables: [Tasks])
class TaskDao extends DatabaseAccessor<AppDatabase> with _$TaskDaoMixin {
  TaskDao(super.db);

  Future<List<TaskEntity>> getAll() => select(tasks).get();
  Future<TaskEntity?> getById(String id) =>
      (select(tasks)..where((t) => t.id.equals(id))).getSingleOrNull();

  /// 活跃任务：waiting / running / paused
  Stream<List<TaskEntity>> watchActive() {
    return (select(tasks)
          ..where((t) => t.status.isIn(
              const [TaskStatus.waiting, TaskStatus.running, TaskStatus.paused]))
          ..orderBy([(t) => OrderingTerm.asc(t.createdAt)]))
        .watch();
  }

  Stream<List<TaskEntity>> watchByStatus(List<String> statuses) {
    return (select(tasks)
          ..where((t) => t.status.isIn(statuses))
          ..orderBy([(t) => OrderingTerm.desc(t.updatedAt)]))
        .watch();
  }

  Stream<TaskEntity?> watchById(String id) {
    return (select(tasks)..where((t) => t.id.equals(id))).watchSingleOrNull();
  }

  Future<List<TaskEntity>> getByStatus(List<String> statuses) {
    return (select(tasks)..where((t) => t.status.isIn(statuses))).get();
  }

  Future<String> insertTask(TasksCompanion entry) async {
    final id = entry.id.present ? entry.id.value : attachedDatabase.newId();
    await into(tasks).insert(entry.copyWith(id: Value(id)));
    return id;
  }

  Future<int> updateProgress(String id, int progressBytes, int totalBytes) {
    return (update(tasks)..where((t) => t.id.equals(id))).write(
      TasksCompanion(
        progressBytes: Value(progressBytes),
        totalBytes: Value(totalBytes),
        updatedAt: Value(DateTime.now()),
      ),
    );
  }

  Future<int> updateStatus(
    String id,
    String status, {
    String? errorMessage,
    int? retryCount,
  }) {
    return (update(tasks)..where((t) => t.id.equals(id))).write(
      TasksCompanion(
        status: Value(status),
        errorMessage: Value(errorMessage),
        retryCount: retryCount != null ? Value(retryCount) : const Value.absent(),
        updatedAt: Value(DateTime.now()),
      ),
    );
  }

  Future<int> updatePayloadJson(String id, String payloadJson) {
    return (update(tasks)..where((t) => t.id.equals(id))).write(
      TasksCompanion(
        payloadJson: Value(payloadJson),
        updatedAt: Value(DateTime.now()),
      ),
    );
  }

  Future<int> deleteById(String id) =>
      (delete(tasks)..where((t) => t.id.equals(id))).go();

  /// 启动时调用：把上次未结束的 running 改回 waiting，准备恢复。
  Future<int> reviveInterrupted() {
    return (update(tasks)..where((t) => t.status.equals(TaskStatus.running))).write(
      TasksCompanion(status: const Value(TaskStatus.waiting), updatedAt: Value(DateTime.now())),
    );
  }
}
