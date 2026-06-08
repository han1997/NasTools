/// 任务状态枚举（与 Drift `tasks.status` 字段同步）。
///
/// 使用字符串而非 enum 是为了便于 schema 演进 —— 增加新状态不需要迁移。
class TaskStatus {
  TaskStatus._();
  static const waiting = 'waiting';
  static const running = 'running';
  static const paused = 'paused';
  static const failed = 'failed';
  static const completed = 'completed';
  static const cancelled = 'cancelled';

  static bool isTerminal(String status) =>
      status == completed || status == cancelled;
  static bool isActive(String status) =>
      status == waiting || status == running || status == paused;
}
