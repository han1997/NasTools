import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';
import '../../../core/utils/bytes_utils.dart';
import '../../../modules/upload/domain/upload_task.dart';
import '../../widgets/progress_ring.dart';
import '../../widgets/status_chip.dart';

class TasksPage extends ConsumerStatefulWidget {
  const TasksPage({super.key});

  @override
  ConsumerState<TasksPage> createState() => _TasksPageState();
}

class _TasksPageState extends ConsumerState<TasksPage> with SingleTickerProviderStateMixin {
  late final TabController _tab = TabController(length: 3, vsync: this);

  @override
  void dispose() {
    _tab.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final db = ref.watch(appDatabaseProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('任务中心'),
        bottom: TabBar(
          controller: _tab,
          tabs: const [
            Tab(text: '活跃'),
            Tab(text: '已完成'),
            Tab(text: '失败'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tab,
        children: [
          _buildList(db, ['waiting', 'running', 'paused']),
          _buildList(db, ['completed']),
          _buildList(db, ['failed', 'cancelled']),
        ],
      ),
    );
  }

  Widget _buildList(AppDatabase db, List<String> statuses) {
    return StreamBuilder<List<TaskEntity>>(
      stream: db.taskDao.watchByStatus(statuses),
      builder: (context, snap) {
        final list = snap.data ?? const [];
        if (list.isEmpty) {
          return const Center(child: Text('暂无任务'));
        }
        return ListView.separated(
          itemCount: list.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (context, i) => _TaskTile(task: list[i]),
        );
      },
    );
  }
}

class _TaskTile extends ConsumerWidget {
  const _TaskTile({required this.task});
  final TaskEntity task;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final progress = task.totalBytes > 0
        ? (task.progressBytes / task.totalBytes).clamp(0.0, 1.0)
        : null;

    String? subtitle;
    try {
      if (task.moduleId == 'upload') {
        final payload = UploadTaskPayload.fromJson(
          jsonDecode(task.payloadJson) as Map<String, dynamic>,
        );
        final cur = payload.currentFile;
        final progressFiles = '${payload.filesDone}/${payload.filesTotal}';
        final bytes =
            '${BytesFormat.human(task.progressBytes)} / ${BytesFormat.human(task.totalBytes)}';
        subtitle = cur == null ? '$progressFiles · $bytes' : '$cur · $progressFiles · $bytes';
      }
    } catch (_) {}

    return ListTile(
      leading: ProgressRing(value: progress),
      title: Text(task.title.isEmpty ? task.type : task.title),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (subtitle != null) Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
          if (task.errorMessage != null)
            Text(
              task.errorMessage!,
              style: TextStyle(color: Theme.of(context).colorScheme.error, fontSize: 12),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          const SizedBox(height: 4),
          Row(children: [StatusChip(status: task.status)]),
        ],
      ),
      trailing: PopupMenuButton<String>(
        onSelected: (v) async {
          final tm = ref.read(taskManagerProvider);
          switch (v) {
            case 'pause':
              await tm.pause(task.id);
              break;
            case 'resume':
              await tm.resume(task.id);
              break;
            case 'cancel':
              await tm.cancel(task.id);
              break;
            case 'retry':
              await tm.retry(task.id);
              break;
            case 'delete':
              await ref.read(appDatabaseProvider).taskDao.deleteById(task.id);
              break;
          }
        },
        itemBuilder: (_) {
          final items = <PopupMenuEntry<String>>[];
          switch (task.status) {
            case 'running':
            case 'waiting':
              items.add(const PopupMenuItem(value: 'pause', child: Text('暂停')));
              items.add(const PopupMenuItem(value: 'cancel', child: Text('取消')));
              break;
            case 'paused':
              items.add(const PopupMenuItem(value: 'resume', child: Text('恢复')));
              items.add(const PopupMenuItem(value: 'cancel', child: Text('取消')));
              break;
            case 'failed':
            case 'cancelled':
              items.add(const PopupMenuItem(value: 'retry', child: Text('重试')));
              items.add(const PopupMenuItem(value: 'delete', child: Text('删除')));
              break;
            case 'completed':
              items.add(const PopupMenuItem(value: 'delete', child: Text('删除')));
              break;
          }
          return items;
        },
      ),
    );
  }
}
