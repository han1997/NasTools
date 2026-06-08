import 'dart:async';

import '../database/app_database.dart';

/// 任务执行上下文 —— 传递给 [TaskExecutor.run] 时使用。
class TaskRunContext {
  TaskRunContext({
    required this.task,
    required this.onProgress,
    required this.shouldCancel,
    required this.shouldPause,
  });

  final TaskEntity task;
  final void Function(int progressBytes, int totalBytes) onProgress;
  final bool Function() shouldCancel;
  final bool Function() shouldPause;
}

/// 任务执行结果。
sealed class TaskRunResult {
  const TaskRunResult();
}

class TaskCompleted extends TaskRunResult {
  const TaskCompleted();
}

class TaskFailedTransient extends TaskRunResult {
  const TaskFailedTransient(this.error);
  final Object error;
}

class TaskFailedPermanent extends TaskRunResult {
  const TaskFailedPermanent(this.error);
  final Object error;
}

class TaskPausedResult extends TaskRunResult {
  const TaskPausedResult();
}

class TaskCancelledResult extends TaskRunResult {
  const TaskCancelledResult();
}

/// 由各模块实现的任务执行器接口。
///
/// 每个 [moduleId] + [type] 对应一个执行器。TaskManager 调度时
/// 按这两个字段查表分发。
abstract class TaskExecutor {
  String get moduleId;
  String get type;

  Future<TaskRunResult> run(TaskRunContext ctx);
}
