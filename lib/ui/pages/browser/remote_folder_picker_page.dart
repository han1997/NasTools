import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../adapters/storage_adapter.dart';
import '../../../core/utils/bytes_utils.dart';
import '../../widgets/empty_state.dart';
import 'remote_browser_controller.dart';

/// 远端文件夹选择器。
///
/// 复用 [RemoteBrowserController] 的导航能力，但去掉"上传到此处"FAB、单项
/// CRUD、多选；只展示目录树并提供一个"选择此目录"按钮，确认后 `pop` 当前
/// 路径返回给调用方（如预设编辑页 / 发起上传页）。
class RemoteFolderPickerPage extends ConsumerWidget {
  const RemoteFolderPickerPage({
    super.key,
    required this.configId,
    required this.initialPath,
    this.title,
  });

  final String configId;
  final String initialPath;
  final String? title;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final key = (configId: configId, initialPath: initialPath);
    final state = ref.watch(remoteBrowserControllerProvider(key));
    final controller = ref.read(remoteBrowserControllerProvider(key).notifier);

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () {
            if (state.path == '/') {
              context.pop();
            } else {
              controller.goParent();
            }
          },
        ),
        title: Text(title ?? state.path, overflow: TextOverflow.ellipsis),
        actions: [
          IconButton(
            icon: const Icon(Icons.create_new_folder_outlined),
            tooltip: '新建文件夹',
            onPressed: () => _mkdir(context, controller),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '刷新',
            onPressed: controller.refresh,
          ),
        ],
      ),
      body: Column(
        children: [
          Material(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                children: [
                  const Icon(Icons.folder_open, size: 18),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      state.path,
                      style: Theme.of(context).textTheme.bodyMedium,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
          ),
          Expanded(child: _body(context, state, controller)),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: FilledButton.icon(
            onPressed: () => context.pop(state.path),
            icon: const Icon(Icons.check),
            label: const Text('选择此目录'),
          ),
        ),
      ),
    );
  }

  Widget _body(
    BuildContext context,
    RemoteBrowserState state,
    RemoteBrowserController controller,
  ) {
    return state.entries.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => EmptyState(
        icon: Icons.error_outline,
        title: '加载失败',
        subtitle: e.toString(),
        action: FilledButton(
          onPressed: controller.refresh,
          child: const Text('重试'),
        ),
      ),
      data: (list) {
        // 只显示目录 —— 文件夹选择场景下文件无意义
        final dirs = list.where((e) => e.isDirectory).toList();
        if (dirs.isEmpty) {
          return const EmptyState(icon: Icons.folder_open, title: '没有子文件夹');
        }
        return ListView.builder(
          itemCount: dirs.length,
          itemBuilder: (context, i) {
            final e = dirs[i];
            return ListTile(
              leading: const Icon(Icons.folder),
              title: Text(e.name),
              subtitle: e.size != null && e.size! > 0
                  ? Text(BytesFormat.human(e.size!))
                  : null,
              trailing: const Icon(Icons.chevron_right),
              onTap: () => controller.goInto(e),
            );
          },
        );
      },
    );
  }

  Future<void> _mkdir(
    BuildContext context,
    RemoteBrowserController controller,
  ) async {
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) {
        final ctrl = TextEditingController();
        return AlertDialog(
          title: const Text('新建文件夹'),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            decoration:
                const InputDecoration(labelText: '名称', border: OutlineInputBorder()),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
            FilledButton(
                onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
                child: const Text('确定')),
          ],
        );
      },
    );
    if (name == null || name.isEmpty) return;
    try {
      await controller.mkdir(name);
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('失败：$e')));
    }
  }
}
