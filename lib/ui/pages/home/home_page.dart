import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';
import '../../widgets/empty_state.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final db = ref.watch(appDatabaseProvider);
    final activeTasksStream = db.taskDao.watchActive();
    final configsStream = db.nasConfigDao.watchAll();

    return Scaffold(
      appBar: AppBar(
        title: const Text('NasTools'),
        actions: [
          IconButton(
            tooltip: '上传预设',
            icon: const Icon(Icons.bookmark_outline),
            onPressed: () => context.push('/presets'),
          ),
          IconButton(
            tooltip: '任务中心',
            icon: const Icon(Icons.task_alt),
            onPressed: () => context.push('/tasks'),
          ),
          IconButton(
            tooltip: '设置',
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => context.push('/settings'),
          ),
        ],
      ),
      body: Column(
        children: [
          _ActiveTaskBar(stream: activeTasksStream),
          Expanded(
            child: StreamBuilder<List<NasConfigEntity>>(
              stream: configsStream,
              builder: (context, snap) {
                final list = snap.data ?? const [];
                if (list.isEmpty) {
                  return EmptyState(
                    icon: Icons.dns_outlined,
                    title: '还没有连接配置',
                    subtitle: '添加一个 WebDAV / SFTP / SMB 连接开始',
                    action: FilledButton.icon(
                      onPressed: () => context.push('/nas/new'),
                      icon: const Icon(Icons.add),
                      label: const Text('新建连接'),
                    ),
                  );
                }
                return ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: list.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 8),
                  itemBuilder: (context, i) {
                    final c = list[i];
                    return Card(
                      child: ListTile(
                        leading: const Icon(Icons.dns, size: 32),
                        title: Text(c.name),
                        subtitle: Text('${c.type.toUpperCase()} · ${c.baseUrl}'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => context.push('/browser/${c.id}'),
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/nas/new'),
        icon: const Icon(Icons.add),
        label: const Text('新建连接'),
      ),
    );
  }
}

class _ActiveTaskBar extends StatelessWidget {
  const _ActiveTaskBar({required this.stream});
  final Stream<List<TaskEntity>> stream;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<TaskEntity>>(
      stream: stream,
      builder: (context, snap) {
        final list = snap.data ?? const [];
        if (list.isEmpty) return const SizedBox.shrink();
        return Material(
          color: Theme.of(context).colorScheme.primaryContainer,
          child: InkWell(
            onTap: () => GoRouter.of(context).push('/tasks'),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  const Icon(Icons.sync, size: 18),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      '${list.length} 个进行中的任务',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ),
                  const Icon(Icons.chevron_right),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
