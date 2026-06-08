import '../database/daos/log_dao.dart';
import 'app_logger.dart';

/// 把日志写入 Drift `logs` 表的 [LogSink]。
class DriftLogSink implements LogSink {
  DriftLogSink(this._dao);
  final LogDao _dao;

  @override
  Future<void> write({
    required String level,
    required String tag,
    required String message,
    String? taskId,
  }) {
    return _dao.append(level: level, tag: tag, message: message, taskId: taskId);
  }
}
