import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';
import '../../../modules/upload/domain/upload_options.dart';
import '../../../modules/upload/providers.dart';
import '../../../modules/upload/service/preset_codec.dart';
import '../../widgets/empty_state.dart';

class PresetListPage extends ConsumerWidget {
  const PresetListPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final presetDao = ref.watch(uploadPresetDaoProvider);
    final db = ref.watch(appDatabaseProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('上传预设')),
      body: StreamBuilder<List<UploadPresetEntity>>(
        stream: presetDao.watchAll(),
        builder: (context, snap) {
          final list = snap.data ?? const [];
          if (list.isEmpty) {
            return EmptyState(
              icon: Icons.bookmark_border,
              title: '还没有上传预设',
              subtitle: '把常用的"本地文件夹 → 远端路径 + 选项"存为模板，一键启动',
              action: FilledButton.icon(
                onPressed: () => context.push('/presets/new'),
                icon: const Icon(Icons.add),
                label: const Text('新建预设'),
              ),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(12),
            itemCount: list.length,
            separatorBuilder: (_, __) => const SizedBox(height: 8),
            itemBuilder: (context, i) => _PresetCard(
              preset: list[i],
              db: db,
            ),
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/presets/new'),
        icon: const Icon(Icons.add),
        label: const Text('新建预设'),
      ),
    );
  }
}

class _PresetCard extends ConsumerStatefulWidget {
  const _PresetCard({required this.preset, required this.db});
  final UploadPresetEntity preset;
  final AppDatabase db;

  @override
  ConsumerState<_PresetCard> createState() => _PresetCardState();
}

class _PresetCardState extends ConsumerState<_PresetCard> {
  bool _running = false;

  @override
  Widget build(BuildContext context) {
    final p = widget.preset;
    final options = PresetCodec.decode(p.optionsJson);
    final filter = options?.filterRegex;
    final delete = options?.deleteAfterUpload ?? false;
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(p.name,
                      style: Theme.of(context).textTheme.titleMedium),
                ),
                PopupMenuButton<String>(
                  onSelected: (v) async {
                    if (v == 'edit') {
                      context.push('/presets/edit/${p.id}');
                    } else if (v == 'delete') {
                      final ok = await _confirm(context, '删除该预设？');
                      if (ok) {
                        await ref
                            .read(uploadPresetDaoProvider)
                            .deleteById(p.id);
                      }
                    }
                  },
                  itemBuilder: (_) => const [
                    PopupMenuItem(value: 'edit', child: Text('编辑')),
                    PopupMenuItem(value: 'delete', child: Text('删除')),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 4),
            FutureBuilder<NasConfigEntity?>(
              future: widget.db.nasConfigDao.getById(p.nasConfigId),
              builder: (context, snap) {
                final nasName = snap.data?.name ?? '(连接已删除)';
                return Text('$nasName → ${p.remoteRoot}',
                    style: Theme.of(context).textTheme.bodySmall);
              },
            ),
            const SizedBox(height: 4),
            Text(
              '本地：${p.localLabel.isEmpty ? p.localUri : p.localLabel}',
              style: Theme.of(context).textTheme.bodySmall,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            if (filter != null && filter.isNotEmpty || delete) ...[
              const SizedBox(height: 6),
              Wrap(
                spacing: 6,
                children: [
                  if (filter != null && filter.isNotEmpty)
                    _Chip(icon: Icons.filter_list, label: filter),
                  if (delete)
                    const _Chip(icon: Icons.delete_outline, label: '上传后删本地'),
                ],
              ),
            ],
            const SizedBox(height: 4),
            if (p.lastRunAt != null)
              Text('上次运行 ${_relative(p.lastRunAt!)}',
                  style: Theme.of(context).textTheme.bodySmall),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton.icon(
                  onPressed: () => context.push('/presets/edit/${p.id}'),
                  icon: const Icon(Icons.edit_outlined),
                  label: const Text('编辑'),
                ),
                FilledButton.icon(
                  onPressed: _running ? null : _run,
                  icon: _running
                      ? const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.play_arrow),
                  label: const Text('运行'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _run() async {
    setState(() => _running = true);
    try {
      final p = widget.preset;
      final options = PresetCodec.decode(p.optionsJson) ?? const UploadOptions();
      final service = ref.read(uploadServiceProvider);
      final id = await service.createFolderUploadTask(
        nasConfigId: p.nasConfigId,
        localRootUri: p.localUri,
        remoteRoot: p.remoteRoot,
        options: options,
        title: p.name,
      );
      await ref.read(uploadPresetDaoProvider).touchLastRun(p.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('已启动：$id')),
      );
      context.go('/tasks');
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('启动失败：$e')));
      }
    } finally {
      if (mounted) setState(() => _running = false);
    }
  }
}

Future<bool> _confirm(BuildContext context, String title) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消')),
        FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('确定')),
      ],
    ),
  );
  return r ?? false;
}

String _relative(DateTime t) {
  final d = DateTime.now().difference(t);
  if (d.inMinutes < 1) return '刚刚';
  if (d.inHours < 1) return '${d.inMinutes} 分钟前';
  if (d.inDays < 1) return '${d.inHours} 小时前';
  if (d.inDays < 30) return '${d.inDays} 天前';
  return '${t.year}-${t.month.toString().padLeft(2, '0')}-${t.day.toString().padLeft(2, '0')}';
}

class _Chip extends StatelessWidget {
  const _Chip({required this.icon, required this.label});
  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14),
          const SizedBox(width: 4),
          Text(label, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}
