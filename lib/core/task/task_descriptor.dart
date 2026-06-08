import 'dart:async';

/// 任务描述符（提交给 TaskManager 时用）。
class TaskDescriptor {
  const TaskDescriptor({
    required this.id,
    required this.moduleId,
    required this.type,
    required this.title,
    this.nasConfigId,
    this.totalBytes = 0,
    this.payload = const {},
    this.priority = 0,
  });

  final String id;
  final String moduleId;
  final String type;
  final String title;
  final String? nasConfigId;
  final int totalBytes;
  final Map<String, Object?> payload;
  final int priority;
}
