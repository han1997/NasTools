import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';
import '../../widgets/empty_state.dart';

class NasConfigListPage extends ConsumerWidget {
  const NasConfigListPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final db = ref.watch(appDatabaseProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('连接管理')),
      body: StreamBuilder<List<NasConfigEntity>>(
        stream: db.nasConfigDao.watchAll(),
        builder: (context, snap) {
          final list = snap.data ?? const [];
          if (list.isEmpty) {
            return EmptyState(
              icon: Icons.dns_outlined,
              title: '还没有连接配置',
              action: FilledButton.icon(
                onPressed: () => context.push('/nas/new'),
                icon: const Icon(Icons.add),
                label: const Text('新建'),
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
                  leading: const Icon(Icons.dns),
                  title: Text(c.name),
                  subtitle: Text('${c.type.toUpperCase()} · ${c.baseUrl}'),
                  trailing: PopupMenuButton<String>(
                    onSelected: (v) async {
                      if (v == 'edit') {
                        context.push('/nas/edit/${c.id}');
                      } else if (v == 'delete') {
                        final ok = await _confirm(context, '删除连接？');
                        if (ok) await db.nasConfigDao.deleteById(c.id);
                      }
                    },
                    itemBuilder: (_) => const [
                      PopupMenuItem(value: 'edit', child: Text('编辑')),
                      PopupMenuItem(value: 'delete', child: Text('删除')),
                    ],
                  ),
                  onTap: () => context.push('/browser/${c.id}'),
                ),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.push('/nas/new'),
        child: const Icon(Icons.add),
      ),
    );
  }

  Future<bool> _confirm(BuildContext context, String title) async {
    final r = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('确定')),
        ],
      ),
    );
    return r ?? false;
  }
}
