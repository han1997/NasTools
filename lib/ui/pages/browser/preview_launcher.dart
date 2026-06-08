import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mime/mime.dart';

import '../../../adapters/storage_adapter.dart';
import '../../../core/cache/preview_cache.dart';
import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';
import '../../../core/utils/bytes_utils.dart';
import '../../../core/utils/path_utils.dart';
import '../../../modules/upload/domain/upload_options.dart';
import '../../../modules/upload/providers.dart';

/// 文件预览 / 下载 / 单文件上传的统一入口。
///
/// 设计要点：
///   - 下载到 app 私有 cache（[PreviewCache]）
///   - 图片走内置 PhotoView 全屏页；其它走系统外部应用 (ACTION_VIEW + FileProvider)
///   - "下载到本地"用 file_picker.saveFile，> 500 MB 拒绝
///   - 单文件上传：file_picker.pickFiles 拿 SAF URI → UploadService.createFileUploadTask
class PreviewLauncher {
  PreviewLauncher._();

  static const _viewerChannel = MethodChannel('nastools/viewer');

  /// 下载并预览。图片用内置全屏页；其它走系统外部应用。
  static Future<void> open(
    BuildContext context,
    WidgetRef ref, {
    required String configId,
    required RemoteEntry entry,
  }) async {
    final ext = RemotePath.extension(entry.name);
    final mime = lookupMimeType(entry.name) ?? 'application/octet-stream';

    final cacheFile = await PreviewCache.fileFor(
      configId: configId,
      remotePath: entry.path,
      extension: ext,
    );

    final cached = await cacheFile.exists() &&
        (entry.size == null || await cacheFile.length() == entry.size);

    if (!cached) {
      final ok = await _downloadWithProgress(
        context,
        ProviderScope.containerOf(context, listen: false),
        configId: configId,
        entry: entry,
        target: cacheFile,
      );
      if (!ok) return;
    }

    if (!context.mounted) return;
    if (mime.startsWith('image/')) {
      await context.push(Uri(path: '/preview/image', queryParameters: {
        'localPath': cacheFile.path,
        'title': entry.name,
      }).toString());
    } else {
      try {
        final opened = await _viewerChannel.invokeMethod<bool>('openExternal', {
          'path': cacheFile.path,
          'mime': mime,
        });
        if (opened != true && context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('没有可处理此文件的应用')),
          );
        }
      } on PlatformException catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('外部打开失败：${e.message ?? e.code}')),
          );
        }
      }
    }
  }

  /// 下载到本地（SAF "另存为"）。受 file_picker.saveFile 的整段 bytes 限制；
  /// > 500 MB 拒绝，50–500 MB 弹确认。
  static Future<void> downloadToLocal(
    BuildContext context, {
    required String configId,
    required RemoteEntry entry,
  }) async {
    final size = entry.size ?? -1;
    const warn = 50 * 1024 * 1024;
    const refuse = 500 * 1024 * 1024;
    if (size > refuse) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
              '文件超过 500 MB（${BytesFormat.human(size)}），请使用 NAS 客户端处理'),
        ),
      );
      return;
    }
    if (size > warn) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('文件较大'),
          content: Text('将一次性读入内存约 ${BytesFormat.human(size)}，确认继续？'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('继续')),
          ],
        ),
      );
      if (ok != true) return;
    }

    final container = ProviderScope.containerOf(context, listen: false);
    final ext = RemotePath.extension(entry.name);
    final cacheFile = await PreviewCache.fileFor(
      configId: configId,
      remotePath: entry.path,
      extension: ext,
    );
    final cached = await cacheFile.exists() &&
        (entry.size == null || await cacheFile.length() == entry.size);

    if (!cached) {
      final ok = await _downloadWithProgress(
        context,
        container,
        configId: configId,
        entry: entry,
        target: cacheFile,
      );
      if (!ok) return;
    }

    final bytes = await cacheFile.readAsBytes();
    if (!context.mounted) return;
    final saved = await FilePicker.platform.saveFile(
      fileName: entry.name,
      bytes: Uint8List.fromList(bytes),
    );
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(saved == null ? '已取消保存' : '已保存到本地')),
    );
  }

  /// 选择本地单文件 → 在 [remoteDir] 下入队 file_upload 任务。
  static Future<void> uploadSingleFile(
    BuildContext context,
    WidgetRef ref, {
    required String configId,
    required String remoteDir,
  }) async {
    final result = await FilePicker.platform.pickFiles(allowMultiple: false);
    if (result == null || result.files.isEmpty) return;
    final f = result.files.first;
    // Android 上 file_picker 返回的 identifier 是 SAF content:// URI；
    // 缺失时无法走 SafLocalSource。
    final uri = f.identifier;
    if (uri == null || uri.isEmpty) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('无法获取文件的 SAF URI')),
        );
      }
      return;
    }
    try {
      final settings = ref.read(settingsRepositoryProvider);
      final id = await ref.read(uploadServiceProvider).createFileUploadTask(
            nasConfigId: configId,
            localFileUri: uri,
            remoteDir: remoteDir,
            options: UploadOptions(
              chunkSizeBytes: settings.chunkSizeMb * 1024 * 1024,
              wifiOnly: settings.wifiOnly,
            ),
            title: f.name,
          );
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('已创建上传任务 $id'),
          action: SnackBarAction(
            label: '查看',
            onPressed: () => context.push('/tasks'),
          ),
        ),
      );
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('失败：$e')));
      }
    }
  }

  // ---- internal -----------------------------------------------------------

  /// 流式下载到 [target] 文件，UI 弹一个带取消按钮的进度对话框。
  /// 返回 true 表示成功；false = 用户取消 / 失败（已 SnackBar 提示）。
  static Future<bool> _downloadWithProgress(
    BuildContext context,
    ProviderContainer container, {
    required String configId,
    required RemoteEntry entry,
    required File target,
  }) async {
    final total = entry.size ?? 0;
    final received = ValueNotifier<int>(0);
    var cancelled = false;
    var dialogPopped = false;

    final dialog = showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: Text('正在准备：${entry.name}',
            maxLines: 1, overflow: TextOverflow.ellipsis),
        content: ValueListenableBuilder<int>(
          valueListenable: received,
          builder: (_, v, __) => Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              LinearProgressIndicator(
                value: total == 0 ? null : v / total,
              ),
              const SizedBox(height: 12),
              Text(total == 0
                  ? BytesFormat.human(v)
                  : '${BytesFormat.human(v)} / ${BytesFormat.human(total)}'),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () {
              cancelled = true;
              dialogPopped = true;
              Navigator.pop(ctx);
            },
            child: const Text('取消'),
          ),
        ],
      ),
    );

    final db = container.read(appDatabaseProvider);
    final factory = container.read(adapterFactoryProvider);
    final NasConfigEntity? cfg = await db.nasConfigDao.getById(configId);
    if (cfg == null) {
      if (!dialogPopped && context.mounted) {
        Navigator.of(context, rootNavigator: true).pop();
      }
      received.dispose();
      return false;
    }
    final adapter = factory.create(cfg);
    final sink = target.openWrite();
    var ok = false;
    try {
      await for (final chunk in adapter.readStream(entry.path)) {
        if (cancelled) break;
        sink.add(chunk);
        received.value += chunk.length;
      }
      await sink.flush();
      ok = !cancelled;
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('下载失败：$e')));
      }
    } finally {
      await sink.close();
      await adapter.dispose();
      if (!dialogPopped && context.mounted) {
        Navigator.of(context, rootNavigator: true).pop();
      }
      received.dispose();
      await dialog;
    }
    if (!ok) {
      try {
        if (await target.exists()) await target.delete();
      } catch (_) {}
    }
    return ok;
  }
}
