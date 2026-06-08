import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../adapters/storage_adapter.dart';
import '../../../core/utils/bytes_utils.dart';
import '../../../core/utils/path_utils.dart';
import '../../widgets/empty_state.dart';
import 'preview_launcher.dart';
import 'remote_browser_controller.dart';

class RemoteBrowserPage extends ConsumerWidget {
  const RemoteBrowserPage({super.key, required this.configId, required this.path});
  final String configId;
  final String path;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final key = (configId: configId, initialPath: path);
    final state = ref.watch(remoteBrowserControllerProvider(key));
    final controller = ref.read(remoteBrowserControllerProvider(key).notifier);

    return PopScope(
      // 多选模式下系统返回键先退出多选，再退页
      canPop: !state.selectionMode,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && state.selectionMode) controller.exitSelection();
      },
      child: Scaffold(
        appBar: _BrowserAppBar(state: state, controller: controller),
        body: _Body(state: state, controller: controller, configId: configId),
        bottomNavigationBar:
            state.selectionMode ? _BatchActionBar(state: state, controller: controller, configId: configId) : null,
        floatingActionButton: state.selectionMode
            ? null
            : _UploadMenuFab(state: state, configId: configId),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// AppBar
// ---------------------------------------------------------------------------

class _BrowserAppBar extends StatelessWidget implements PreferredSizeWidget {
  const _BrowserAppBar({required this.state, required this.controller});
  final RemoteBrowserState state;
  final RemoteBrowserController controller;

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    if (state.selectionMode) {
      return AppBar(
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: controller.exitSelection,
        ),
        title: Text('已选 ${state.selected.length}'),
      );
    }
    return AppBar(
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
      title: Text(state.path, overflow: TextOverflow.ellipsis),
      actions: [
        IconButton(
          icon: Icon(state.viewMode == BrowserViewMode.list
              ? Icons.grid_view_outlined
              : Icons.view_list_outlined),
          tooltip: state.viewMode == BrowserViewMode.list ? '网格视图' : '列表视图',
          onPressed: controller.toggleViewMode,
        ),
        IconButton(
          icon: const Icon(Icons.create_new_folder_outlined),
          tooltip: '新建文件夹',
          onPressed: () => _promptMkdir(context, controller),
        ),
        IconButton(
          icon: const Icon(Icons.refresh),
          tooltip: '刷新',
          onPressed: controller.refresh,
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Body
// ---------------------------------------------------------------------------

class _Body extends ConsumerWidget {
  const _Body({required this.state, required this.controller, required this.configId});
  final RemoteBrowserState state;
  final RemoteBrowserController controller;
  final String configId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
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
        if (list.isEmpty) {
          return const EmptyState(icon: Icons.folder_open, title: '空目录');
        }
        if (state.viewMode == BrowserViewMode.grid) {
          return GridView.builder(
            padding: const EdgeInsets.all(8),
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: 120,
              mainAxisSpacing: 8,
              crossAxisSpacing: 8,
              childAspectRatio: 0.85,
            ),
            itemCount: list.length,
            itemBuilder: (context, i) => _EntryGridTile(
              entry: list[i],
              selected: state.selected.contains(list[i].path),
              selectionMode: state.selectionMode,
              onTap: () => _onTap(context, ref, list[i]),
              onLongPress: () => _onLongPress(list[i]),
              onAction: () => _showActionSheet(context, ref, list[i]),
            ),
          );
        }
        return ListView.builder(
          itemCount: list.length,
          itemBuilder: (context, i) => _EntryTile(
            entry: list[i],
            selected: state.selected.contains(list[i].path),
            selectionMode: state.selectionMode,
            onTap: () => _onTap(context, ref, list[i]),
            onLongPress: () => _onLongPress(list[i]),
            onAction: () => _showActionSheet(context, ref, list[i]),
          ),
        );
      },
    );
  }

  void _onTap(BuildContext context, WidgetRef ref, RemoteEntry e) {
    if (state.selectionMode) {
      controller.toggleSelected(e);
      return;
    }
    if (e.isDirectory) {
      controller.goInto(e);
    } else {
      PreviewLauncher.open(context, ref, configId: configId, entry: e);
    }
  }

  void _onLongPress(RemoteEntry e) {
    if (!state.selectionMode) {
      controller.enterSelection(e);
    } else {
      controller.toggleSelected(e);
    }
  }

  void _showActionSheet(BuildContext context, WidgetRef ref, RemoteEntry e) {
    if (state.selectionMode) return;
    showModalBottomSheet<void>(
      context: context,
      builder: (sheetCtx) => _EntryActionSheet(
        parentContext: context,
        entry: e,
        configId: configId,
        controller: controller,
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Tiles
// ---------------------------------------------------------------------------

class _EntryTile extends StatelessWidget {
  const _EntryTile({
    required this.entry,
    required this.selected,
    required this.selectionMode,
    required this.onTap,
    required this.onLongPress,
    required this.onAction,
  });
  final RemoteEntry entry;
  final bool selected;
  final bool selectionMode;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    final leading = selectionMode
        ? Checkbox(
            value: selected,
            onChanged: (_) => onTap(),
          )
        : Icon(entry.isDirectory ? Icons.folder : Icons.insert_drive_file_outlined);
    return ListTile(
      leading: leading,
      title: Text(entry.name),
      subtitle: entry.isDirectory
          ? null
          : Text(BytesFormat.human(entry.size ?? 0)),
      trailing: selectionMode
          ? null
          : IconButton(
              icon: const Icon(Icons.more_vert),
              onPressed: onAction,
            ),
      onTap: onTap,
      onLongPress: onLongPress,
      selected: selected,
    );
  }
}

class _EntryGridTile extends StatelessWidget {
  const _EntryGridTile({
    required this.entry,
    required this.selected,
    required this.selectionMode,
    required this.onTap,
    required this.onLongPress,
    required this.onAction,
  });
  final RemoteEntry entry;
  final bool selected;
  final bool selectionMode;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Stack(
        children: [
          Padding(
            padding: const EdgeInsets.all(8),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  entry.isDirectory ? Icons.folder : Icons.insert_drive_file_outlined,
                  size: 40,
                  color: selected
                      ? Theme.of(context).colorScheme.primary
                      : null,
                ),
                const SizedBox(height: 6),
                Text(
                  entry.name,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
          if (selectionMode)
            Positioned(
              top: 2,
              left: 2,
              child: Checkbox(
                value: selected,
                onChanged: (_) => onTap(),
              ),
            )
          else
            Positioned(
              top: -4,
              right: -4,
              child: IconButton(
                icon: const Icon(Icons.more_vert, size: 18),
                onPressed: onAction,
              ),
            ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Single-entry action sheet
// ---------------------------------------------------------------------------

class _EntryActionSheet extends StatelessWidget {
  const _EntryActionSheet({
    required this.parentContext,
    required this.entry,
    required this.configId,
    required this.controller,
  });
  final BuildContext parentContext;
  final RemoteEntry entry;
  final String configId;
  final RemoteBrowserController controller;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: Icon(entry.isDirectory ? Icons.folder : Icons.insert_drive_file_outlined),
            title: Text(entry.name),
            subtitle: Text(entry.isDirectory
                ? '文件夹'
                : BytesFormat.human(entry.size ?? 0)),
          ),
          const Divider(height: 1),
          ListTile(
            leading: const Icon(Icons.drive_file_rename_outline),
            title: const Text('重命名'),
            onTap: () {
              Navigator.pop(context);
              _rename(parentContext);
            },
          ),
          ListTile(
            leading: const Icon(Icons.drive_file_move_outline),
            title: const Text('移动到…'),
            onTap: () {
              Navigator.pop(context);
              _moveOrCopy(parentContext, copy: false);
            },
          ),
          ListTile(
            leading: const Icon(Icons.copy_all_outlined),
            title: const Text('复制到…'),
            onTap: () {
              Navigator.pop(context);
              _moveOrCopy(parentContext, copy: true);
            },
          ),
          if (!entry.isDirectory)
            ListTile(
              leading: const Icon(Icons.download_outlined),
              title: const Text('下载到本地…'),
              onTap: () {
                Navigator.pop(context);
                PreviewLauncher.downloadToLocal(
                  parentContext,
                  configId: configId,
                  entry: entry,
                );
              },
            ),
          ListTile(
            leading: Icon(Icons.delete_outline, color: Theme.of(context).colorScheme.error),
            title: Text('删除', style: TextStyle(color: Theme.of(context).colorScheme.error)),
            onTap: () {
              Navigator.pop(context);
              _delete(parentContext);
            },
          ),
        ],
      ),
    );
  }

  Future<void> _rename(BuildContext context) async {
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) {
        final c = TextEditingController(text: entry.name);
        return AlertDialog(
          title: const Text('重命名'),
          content: TextField(
            controller: c,
            autofocus: true,
            decoration: const InputDecoration(border: OutlineInputBorder()),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
            FilledButton(
                onPressed: () => Navigator.pop(ctx, c.text.trim()),
                child: const Text('确定')),
          ],
        );
      },
    );
    if (name == null || name.isEmpty || name == entry.name) return;
    try {
      await controller.rename(entry, name);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('失败：$e')));
      }
    }
  }

  Future<void> _moveOrCopy(BuildContext context, {required bool copy}) async {
    final selected = await context.push<String>(
      Uri(path: '/folder_picker/$configId', queryParameters: {
        'path': RemotePath.parent(entry.path),
        'title': copy ? '复制到…' : '移动到…',
      }).toString(),
    );
    if (selected == null) return;
    if (selected == RemotePath.parent(entry.path) && !copy) {
      // 移到原目录 = 空操作
      return;
    }
    try {
      if (copy) {
        await controller.copyTo(entry, selected);
      } else {
        await controller.moveTo(entry, selected);
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('失败：$e')));
      }
    }
  }

  Future<void> _delete(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('删除「${entry.name}」？'),
        content: Text(entry.isDirectory ? '该文件夹下所有内容将被删除' : '不可恢复'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await controller.deleteOne(entry);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('失败：$e')));
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Batch action bar
// ---------------------------------------------------------------------------

class _BatchActionBar extends StatelessWidget {
  const _BatchActionBar({required this.state, required this.controller, required this.configId});
  final RemoteBrowserState state;
  final RemoteBrowserController controller;
  final String configId;

  @override
  Widget build(BuildContext context) {
    final selectedEntries = state.entries.maybeWhen(
      data: (list) => list.where((e) => state.selected.contains(e.path)).toList(),
      orElse: () => <RemoteEntry>[],
    );
    final n = selectedEntries.length;
    return SafeArea(
      child: Material(
        elevation: 8,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            children: [
              Text('已选 $n 项'),
              const Spacer(),
              TextButton.icon(
                onPressed: n == 0
                    ? null
                    : () => _batchMove(context, selectedEntries),
                icon: const Icon(Icons.drive_file_move_outline),
                label: const Text('移动'),
              ),
              const SizedBox(width: 8),
              FilledButton.icon(
                style: FilledButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.error,
                ),
                onPressed: n == 0
                    ? null
                    : () => _batchDelete(context, selectedEntries),
                icon: const Icon(Icons.delete_outline),
                label: const Text('删除'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _batchDelete(BuildContext context, List<RemoteEntry> es) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('删除选中的 ${es.length} 项？'),
        content: const Text('不可恢复'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await _runWithProgress<({int ok, int failed})>(
      context: context,
      total: es.length,
      title: '正在删除…',
      run: (onProgress) => controller.batchDelete(es, onProgress: onProgress),
      done: (r) =>
          '已删除 ${r.ok}/${es.length}${r.failed > 0 ? '，失败 ${r.failed}' : ''}',
    );
    controller.exitSelection();
  }

  Future<void> _batchMove(BuildContext context, List<RemoteEntry> es) async {
    final dst = await context.push<String>(
      Uri(path: '/folder_picker/$configId', queryParameters: {
        'path': state.path,
        'title': '移动到…',
      }).toString(),
    );
    if (dst == null) return;
    await _runWithProgress<({int ok, int failed})>(
      context: context,
      total: es.length,
      title: '正在移动…',
      run: (onProgress) =>
          controller.batchMoveTo(es, dst, onProgress: onProgress),
      done: (r) =>
          '已移动 ${r.ok}/${es.length}${r.failed > 0 ? '，失败 ${r.failed}' : ''}',
    );
    controller.exitSelection();
  }

  /// 弹出一个不可取消的进度对话框；任务执行期间动态更新计数；完成后自动 pop 并
  /// 弹 SnackBar 反馈。
  Future<void> _runWithProgress<T>({
    required BuildContext context,
    required int total,
    required String title,
    required Future<T> Function(void Function(int done, int total) onProgress) run,
    required String Function(T result) done,
  }) async {
    final progress = ValueNotifier<int>(0);
    final dialogFuture = showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: ValueListenableBuilder<int>(
          valueListenable: progress,
          builder: (_, v, __) => Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              LinearProgressIndicator(
                value: total == 0 ? null : v / total,
              ),
              const SizedBox(height: 12),
              Text('$v / $total'),
            ],
          ),
        ),
      ),
    );
    try {
      final r = await run((d, _) => progress.value = d);
      if (context.mounted) {
        Navigator.of(context, rootNavigator: true).pop();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(done(r))));
      }
      // 等 dialog 关闭，避免 dispose 时还在 widget tree
      await dialogFuture;
    } catch (e) {
      if (context.mounted) {
        Navigator.of(context, rootNavigator: true).pop();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('失败：$e')));
      }
    } finally {
      progress.dispose();
    }
  }
}

// ---------------------------------------------------------------------------
// Upload menu FAB
// ---------------------------------------------------------------------------

class _UploadMenuFab extends ConsumerWidget {
  const _UploadMenuFab({required this.state, required this.configId});
  final RemoteBrowserState state;
  final String configId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return FloatingActionButton.extended(
      onPressed: () => _showMenu(context, ref),
      icon: const Icon(Icons.cloud_upload),
      label: const Text('上传'),
    );
  }

  void _showMenu(BuildContext context, WidgetRef ref) {
    showModalBottomSheet<void>(
      context: context,
      builder: (sheetCtx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.folder_outlined),
              title: const Text('上传文件夹到此处'),
              onTap: () {
                Navigator.pop(sheetCtx);
                context.push(Uri(path: '/upload/launch', queryParameters: {
                  'configId': configId,
                  'remoteRoot': state.path,
                }).toString());
              },
            ),
            ListTile(
              leading: const Icon(Icons.insert_drive_file_outlined),
              title: const Text('上传单文件到此处'),
              onTap: () async {
                Navigator.pop(sheetCtx);
                await PreviewLauncher.uploadSingleFile(
                  context,
                  ref,
                  configId: configId,
                  remoteDir: state.path,
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// mkdir prompt
// ---------------------------------------------------------------------------

Future<void> _promptMkdir(
  BuildContext context,
  RemoteBrowserController controller,
) async {
  final name = await showDialog<String>(
    context: context,
    builder: (ctx) {
      final c = TextEditingController();
      return AlertDialog(
        title: const Text('新建文件夹'),
        content: TextField(
          controller: c,
          autofocus: true,
          decoration: const InputDecoration(labelText: '名称', border: OutlineInputBorder()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, c.text.trim()),
              child: const Text('确定')),
        ],
      );
    },
  );
  if (name == null || name.isEmpty) return;
  try {
    await controller.mkdir(name);
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('失败：$e')));
    }
  }
}
