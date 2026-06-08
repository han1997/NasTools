import 'dart:async';
import 'dart:convert';

import 'package:uuid/uuid.dart';

import '../../../adapters/storage_adapter.dart';
import '../../../adapters/webdav/webdav_adapter.dart' show ResumeNotSupported;
import '../../../core/database/app_database.dart';
import '../../../core/logger/app_logger.dart';
import '../../../core/task/task_descriptor.dart';
import '../../../core/task/task_manager.dart';
import '../../../core/utils/path_utils.dart';
import '../domain/upload_chunk.dart';
import '../domain/upload_options.dart';
import '../domain/upload_task.dart';
import '../domain/upload_task_type.dart';
import 'folder_walker.dart';
import 'local_source.dart';

/// 单文件上传一次的最终结果。
enum UploadOneOutcome { done, skipped, cancelled, paused }

/// Upload 模块对外的高层 API。UI 端调用：
/// * [createFolderUploadTask] —— 创建并入队一个"上传文件夹"任务
/// * [createFileUploadTask]   —— 创建并入队一个"上传单文件"任务
class UploadService {
  UploadService({
    required AppDatabase database,
    required TaskManager taskManager,
    required AppLogger logger,
    required LocalSource localSource,
  })  : _db = database,
        _tm = taskManager,
        _log = logger,
        _src = localSource;

  final AppDatabase _db;
  final TaskManager _tm;
  final AppLogger _log;
  final LocalSource _src;

  Future<String> createFolderUploadTask({
    required String nasConfigId,
    required String localRootUri,
    required String remoteRoot,
    required UploadOptions options,
    String? title,
  }) async {
    final id = const Uuid().v4();
    final rootName = (await _src.stat(localRootUri))?.name ?? 'upload';

    final payload = UploadTaskPayload(
      sourceUri: localRootUri,
      sourceKind: 'saf',
      remoteRoot: RemotePath.normalize(remoteRoot),
      options: options,
    );

    await _tm.enqueue(TaskDescriptor(
      id: id,
      moduleId: 'upload',
      type: UploadTaskType.folderUpload,
      title: title ?? rootName,
      nasConfigId: nasConfigId,
      totalBytes: 0,
      payload: payload.toJson(),
    ));

    return id;
  }

  /// 创建单文件上传任务。
  ///
  /// [remoteDir] 是目标父目录；最终远端路径 = `remoteDir/basename(localFileUri)`。
  /// 复用 [UploadTaskPayload] 字段：sourceUri = 文件 URI，remoteRoot = 父目录。
  Future<String> createFileUploadTask({
    required String nasConfigId,
    required String localFileUri,
    required String remoteDir,
    required UploadOptions options,
    String? title,
  }) async {
    final id = const Uuid().v4();
    final stat = await _src.stat(localFileUri);
    if (stat == null) throw StateError('本地文件不存在：$localFileUri');
    if (stat.isDirectory) {
      throw ArgumentError('createFileUploadTask 入参指向文件夹，请改用 createFolderUploadTask');
    }

    final payload = UploadTaskPayload(
      sourceUri: localFileUri,
      sourceKind: 'saf',
      remoteRoot: RemotePath.normalize(remoteDir),
      options: options,
      filesTotal: 1,
    );

    await _tm.enqueue(TaskDescriptor(
      id: id,
      moduleId: 'upload',
      type: UploadTaskType.fileUpload,
      title: title ?? stat.name,
      nasConfigId: nasConfigId,
      totalBytes: stat.size,
      payload: payload.toJson(),
    ));

    return id;
  }

  /// Executor 在运行时调用 —— 把当前文件名 / 计数等元数据写回 payload。
  Future<void> updatePayload(String taskId, UploadTaskPayload payload) async {
    await _db.taskDao.updatePayloadJson(taskId, jsonEncode(payload.toJson()));
  }

  /// 给 executor 用：把 walker 暴露出去。
  Stream<LocalFileEntry> walk(String rootUri, {String? filterRegex}) {
    return FolderWalker(_src).walk(rootUri, filterRegex: filterRegex);
  }

  Future<({int filesTotal, int bytesTotal})> summarize(
    String rootUri, {
    String? filterRegex,
  }) {
    return FolderWalker(_src).summary(rootUri, filterRegex: filterRegex);
  }

  LocalSource get localSource => _src;

  // ---- 单文件上传核心（folder/file 两个 executor 共用） --------------------

  /// 上传单个文件，包含冲突处理 / 续传 / rename / 暂停 / 取消 / ResumeNotSupported 降级。
  ///
  /// [cancel] / [pause] 由调用方提供，executor 间定期被 watcher 检查；
  /// [onChunkSent] 单次进度增量（负数 = 失败回滚）。
  Future<UploadOneOutcome> uploadOne({
    required StorageAdapter adapter,
    required LocalFileEntry file,
    required String remotePath,
    required UploadOptions options,
    required bool Function() cancel,
    required bool Function() pause,
    required void Function(int incr) onChunkSent,
  }) async {
    int offset = 0;
    var finalPath = remotePath;
    final canResume = adapter.capabilities.supportsResume &&
        options.overwriteMode == OverwriteMode.resumeOrOverwrite;

    if (options.overwriteMode == OverwriteMode.skip) {
      final existing = await adapter.stat(remotePath);
      if (existing != null) {
        onChunkSent(file.size);
        return UploadOneOutcome.skipped;
      }
    } else if (options.overwriteMode == OverwriteMode.rename) {
      final existing = await adapter.stat(remotePath);
      if (existing != null) {
        finalPath = await findFreeName(adapter, remotePath);
      }
    } else if (canResume) {
      final existing = await adapter.stat(remotePath);
      if (existing != null && existing.size != null) {
        if (existing.size == file.size) {
          onChunkSent(file.size);
          return UploadOneOutcome.done;
        }
        if (existing.size! > 0 && existing.size! < file.size) {
          offset = existing.size!;
        }
      }
    }

    final parent = RemotePath.parent(finalPath);
    if (parent != '/') {
      await adapter.mkdir(parent);
    }

    final cancelToken = TransferCancelToken();
    UploadOneOutcome? signaled;

    final watcher = Timer.periodic(const Duration(milliseconds: 200), (_) {
      if (signaled != null) return;
      if (cancel()) {
        signaled = UploadOneOutcome.cancelled;
        cancelToken.cancel();
      } else if (pause()) {
        signaled = UploadOneOutcome.paused;
        cancelToken.cancel();
      }
    });

    Stream<List<int>> newStream(int from) => _src.readChunked(
          file.uri,
          chunkSize: options.chunkSizeBytes,
          offset: from,
        );

    var fileReported = 0;
    void report(int delta) {
      fileReported += delta;
      onChunkSent(delta);
    }

    if (offset > 0) report(offset);

    Future<void> doUpload(int from) async {
      var localPrev = 0;
      await adapter.writeStream(
        finalPath,
        newStream(from),
        totalLength: file.size,
        offset: from,
        cancel: cancelToken,
        onProgress: (sent) {
          final delta = sent - localPrev;
          if (delta > 0) {
            localPrev = sent;
            report(delta);
          }
        },
      );
    }

    try {
      try {
        await doUpload(offset);
      } on ResumeNotSupported catch (e) {
        _log.warn('upload',
            '${file.relativePath}: 服务端不支持续传，降级整传 ($e)');
        if (fileReported > 0) {
          report(-fileReported);
        }
        await doUpload(0);
      }
      return UploadOneOutcome.done;
    } catch (e) {
      if (signaled != null) return signaled!;
      rethrow;
    } finally {
      watcher.cancel();
    }
  }

  /// 在 [path] 同目录下找首个不冲突的 `${stem}_${n}${ext}` 路径（n=1..9999）。
  Future<String> findFreeName(StorageAdapter adapter, String path) async {
    final parent = RemotePath.parent(path);
    final base = RemotePath.basename(path);
    final dot = base.lastIndexOf('.');
    final stem = dot > 0 ? base.substring(0, dot) : base;
    final ext = dot > 0 ? base.substring(dot) : '';
    for (var n = 1; n <= 9999; n++) {
      final candidate = parent == '/'
          ? '/${stem}_$n$ext'
          : '$parent/${stem}_$n$ext';
      if (await adapter.stat(candidate) == null) return candidate;
    }
    throw Exception('rename 找不到可用文件名（9999 内全部占用）: $path');
  }
}
