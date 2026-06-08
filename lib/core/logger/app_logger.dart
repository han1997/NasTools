import 'dart:async';

/// 简单的应用日志接口。
///
/// 实现策略：先输出到 `developer.log`（开发期可见），同时通过 [sink]
/// 持久化到 Drift（如果绑定了）。生产环境下 trace/debug 仅落库不输出。
class AppLogger {
  AppLogger({this.sink, this.minLevel = LogLevel.info});

  LogSink? sink;
  LogLevel minLevel;

  void trace(String tag, String message, {String? taskId}) =>
      _log(LogLevel.trace, tag, message, taskId);
  void debug(String tag, String message, {String? taskId}) =>
      _log(LogLevel.debug, tag, message, taskId);
  void info(String tag, String message, {String? taskId}) =>
      _log(LogLevel.info, tag, message, taskId);
  void warn(String tag, String message, {String? taskId}) =>
      _log(LogLevel.warn, tag, message, taskId);
  void error(String tag, String message, {String? taskId}) =>
      _log(LogLevel.error, tag, message, taskId);

  void _log(LogLevel level, String tag, String message, String? taskId) {
    if (level.index < minLevel.index) return;
    // ignore: avoid_print
    print('[${level.name.toUpperCase()}] $tag: $message');
    final s = sink;
    if (s != null) {
      // 异步写入，不阻塞调用方。
      unawaited(s.write(level: level.name, tag: tag, message: message, taskId: taskId));
    }
  }
}

enum LogLevel { trace, debug, info, warn, error }

abstract class LogSink {
  Future<void> write({
    required String level,
    required String tag,
    required String message,
    String? taskId,
  });
}
