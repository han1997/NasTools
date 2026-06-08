import 'dart:async';

import '../database/app_database.dart';
import '../task/task_state.dart';
import '../utils/bytes_utils.dart';
import 'service_bridge.dart';

/// 监听 Drift 中"活跃任务"流，把摘要推给 Android 通知。
///
/// 没有活跃任务时调度停止 FGS（保留 1 分钟宽限以应对快速的任务交替）。
class NotificationController {
  NotificationController(this._db);
  final AppDatabase _db;

  StreamSubscription<List<TaskEntity>>? _sub;
  Timer? _idleTimer;

  void start() {
    _sub?.cancel();
    _sub = _db.taskDao.watchActive().listen(_onChanged);
  }

  Future<void> stop() async {
    await _sub?.cancel();
    _sub = null;
    _idleTimer?.cancel();
    _idleTimer = null;
  }

  void _onChanged(List<TaskEntity> active) {
    if (active.isEmpty) {
      // 1 分钟无活跃任务 → 停 FGS
      _idleTimer?.cancel();
      _idleTimer = Timer(const Duration(minutes: 1), () {
        ServiceBridge.instance.stopService();
      });
      return;
    }
    _idleTimer?.cancel();
    _idleTimer = null;

    final running = active.where((t) => t.status == TaskStatus.running).toList();
    final waiting = active.where((t) => t.status == TaskStatus.waiting).length;

    final title = running.isEmpty
        ? '${active.length} 个任务排队中'
        : '正在传输 (${running.length})';
    final cur = running.isNotEmpty ? running.first : active.first;
    final msg = _formatLine(cur, waiting);

    final progress = cur.totalBytes > 0 ? cur.progressBytes : -1;
    final max = cur.totalBytes;

    ServiceBridge.instance.updateNotification(
      title: title,
      message: msg,
      progress: progress,
      max: max,
    );
  }

  String _formatLine(TaskEntity t, int waitingCount) {
    final base = t.title.isEmpty ? t.type : t.title;
    if (t.totalBytes <= 0) return base;
    final p = BytesFormat.human(t.progressBytes);
    final total = BytesFormat.human(t.totalBytes);
    final tail = waitingCount > 0 ? ' (+${waitingCount} 排队)' : '';
    return '$base · $p / $total$tail';
  }
}
