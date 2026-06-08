import 'dart:async';
import 'dart:convert';

import 'package:drift/drift.dart';

import '../database/app_database.dart';
import '../logger/app_logger.dart';
import 'concurrency_gate.dart';
import 'retry_policy.dart';
import 'task_descriptor.dart';
import 'task_executor.dart';
import 'task_state.dart';

/// 全局任务调度器。
///
/// 设计要点：
/// * 通常运行在 service isolate（后台 FGS 内）；UI 端只读 Drift。
/// * 控制指令（pause/resume/cancel）通过更新 `tasks.status`，
///   调度循环监听 status 变化做出反应 —— 跨 isolate 一致。
/// * Executor 按 (moduleId, type) 注册；模块在启动时 register。
class TaskManager {
  TaskManager({
    required AppDatabase database,
    required AppLogger logger,
    int maxConcurrent = 3,
    this.retryPolicy = const RetryPolicy(),
  })  : _db = database,
        _log = logger,
        _gate = ConcurrencyGate(maxConcurrent);

  final AppDatabase _db;
  final AppLogger _log;
  final ConcurrencyGate _gate;
  final RetryPolicy retryPolicy;

  final Map<String, TaskExecutor> _executors = {};
  final Map<String, _RunningHandle> _running = {};

  StreamSubscription<List<TaskEntity>>? _waitingSub;
  bool _started = false;

  /// 注册执行器。
  void registerExecutor(TaskExecutor exec) {
    final key = _execKey(exec.moduleId, exec.type);
    if (_executors.containsKey(key)) {
      _log.warn('TaskManager', '重复注册 executor: $key —— 后注册覆盖');
    }
    _executors[key] = exec;
  }

  /// 启动调度。冷启动时把 running 改回 waiting（reviveInterrupted），
  /// 然后订阅 waiting 任务流，逐个调度。
  Future<void> start() async {
    if (_started) return;
    _started = true;

    final revived = await _db.taskDao.reviveInterrupted();
    if (revived > 0) {
      _log.info('TaskManager', '复活 $revived 个中断任务');
    }

    _waitingSub = _db.taskDao.watchByStatus([TaskStatus.waiting]).listen(_onWaitingChanged);
  }

  Future<void> stop() async {
    await _waitingSub?.cancel();
    _waitingSub = null;
    for (final h in _running.values) {
      h.requestPause();
    }
    _started = false;
  }

  /// 投递任务。返回任务 id（与 descriptor.id 相同）。
  Future<String> enqueue(TaskDescriptor d) async {
    await _db.taskDao.insertTask(TasksCompanion.insert(
      id: d.id,
      moduleId: d.moduleId,
      type: d.type,
      nasConfigId: Value(d.nasConfigId),
      status: const Value(TaskStatus.waiting),
      progressBytes: const Value(0),
      totalBytes: Value(d.totalBytes),
      title: Value(d.title),
      payloadJson: Value(jsonEncode(d.payload)),
      priority: Value(d.priority),
    ));
    return d.id;
  }

  Future<void> pause(String id) async {
    await _db.taskDao.updateStatus(id, TaskStatus.paused);
    _running[id]?.requestPause();
  }

  Future<void> resume(String id) async {
    await _db.taskDao.updateStatus(id, TaskStatus.waiting);
  }

  Future<void> cancel(String id) async {
    await _db.taskDao.updateStatus(id, TaskStatus.cancelled);
    _running[id]?.requestCancel();
  }

  Future<void> retry(String id) async {
    await _db.taskDao.updateStatus(id, TaskStatus.waiting,
        errorMessage: null, retryCount: 0);
  }

  // --------- 内部 ---------

  void _onWaitingChanged(List<TaskEntity> waiting) {
    for (final t in waiting) {
      if (_running.containsKey(t.id)) continue;
      _tryRun(t);
    }
  }

  Future<void> _tryRun(TaskEntity task) async {
    final exec = _executors[_execKey(task.moduleId, task.type)];
    if (exec == null) {
      _log.error('TaskManager', '没有找到 executor: ${task.moduleId}/${task.type}');
      await _db.taskDao.updateStatus(task.id, TaskStatus.failed,
          errorMessage: '没有注册的执行器');
      return;
    }

    await _gate.acquire();
    final handle = _RunningHandle();
    _running[task.id] = handle;

    try {
      await _db.taskDao.updateStatus(task.id, TaskStatus.running);
      final freshTask = await _db.taskDao.getById(task.id) ?? task;

      final ctx = TaskRunContext(
        task: freshTask,
        onProgress: (p, total) {
          _db.taskDao.updateProgress(task.id, p, total);
        },
        shouldCancel: () => handle.cancelRequested,
        shouldPause: () => handle.pauseRequested,
      );

      final result = await exec.run(ctx);

      switch (result) {
        case TaskCompleted():
          await _db.taskDao.updateStatus(task.id, TaskStatus.completed);
        case TaskCancelledResult():
          await _db.taskDao.updateStatus(task.id, TaskStatus.cancelled);
        case TaskPausedResult():
          // pause 已经在 pause() 中写过 status；这里确保。
          await _db.taskDao.updateStatus(task.id, TaskStatus.paused);
        case TaskFailedTransient(:final error):
          await _handleTransientFail(freshTask, error);
        case TaskFailedPermanent(:final error):
          _log.error('TaskManager', '任务永久失败 ${task.id}: $error');
          await _db.taskDao.updateStatus(task.id, TaskStatus.failed,
              errorMessage: error.toString());
      }
    } catch (e, st) {
      _log.error('TaskManager', '任务异常 ${task.id}: $e\n$st');
      await _handleTransientFail(task, e);
    } finally {
      _running.remove(task.id);
      _gate.release();
    }
  }

  Future<void> _handleTransientFail(TaskEntity task, Object error) async {
    final nextAttempt = task.retryCount + 1;
    if (!retryPolicy.shouldRetry(nextAttempt)) {
      await _db.taskDao.updateStatus(task.id, TaskStatus.failed,
          errorMessage: error.toString(), retryCount: nextAttempt);
      return;
    }
    final delay = retryPolicy.delayFor(nextAttempt);
    _log.warn(
      'TaskManager',
      '任务 ${task.id} 第 $nextAttempt 次重试，等待 ${delay.inSeconds}s: $error',
    );
    await Future<void>.delayed(delay);
    await _db.taskDao.updateStatus(task.id, TaskStatus.waiting,
        errorMessage: error.toString(), retryCount: nextAttempt);
  }

  String _execKey(String moduleId, String type) => '$moduleId/$type';
}

class _RunningHandle {
  bool pauseRequested = false;
  bool cancelRequested = false;
  void requestPause() => pauseRequested = true;
  void requestCancel() => cancelRequested = true;
}
