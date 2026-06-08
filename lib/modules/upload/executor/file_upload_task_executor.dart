import 'dart:async';
import 'dart:convert';

import '../../../adapters/adapter_factory.dart';
import '../../../core/database/app_database.dart';
import '../../../core/logger/app_logger.dart';
import '../../../core/task/task_executor.dart';
import '../../../core/utils/path_utils.dart';
import '../domain/upload_chunk.dart';
import '../domain/upload_task.dart';
import '../domain/upload_task_type.dart';
import '../service/upload_service.dart';

/// file_upload 任务的执行器。
///
/// 与 [UploadTaskExecutor] 共用 [UploadService.uploadOne]，但跳过 walker /
/// summary —— payload.sourceUri 直接是单文件 URI，最终远端路径 =
/// `payload.remoteRoot / basename(sourceUri 的本地名)`。
class FileUploadTaskExecutor implements TaskExecutor {
  FileUploadTaskExecutor({
    required AppDatabase database,
    required AppLogger logger,
    required AdapterFactory adapterFactory,
    required UploadService uploadService,
  })  : _db = database,
        _log = logger,
        _factory = adapterFactory,
        _service = uploadService;

  final AppDatabase _db;
  final AppLogger _log;
  final AdapterFactory _factory;
  final UploadService _service;

  @override
  String get moduleId => 'upload';

  @override
  String get type => UploadTaskType.fileUpload;

  @override
  Future<TaskRunResult> run(TaskRunContext ctx) async {
    final task = ctx.task;
    final payload = UploadTaskPayload.fromJson(
      jsonDecode(task.payloadJson) as Map<String, dynamic>,
    );
    final configId = task.nasConfigId;
    if (configId == null) {
      return const TaskFailedPermanent('upload 任务缺少 nasConfigId');
    }
    final nasConfig = await _db.nasConfigDao.getById(configId);
    if (nasConfig == null) {
      return const TaskFailedPermanent('NAS 配置已被删除');
    }

    final stat = await _service.localSource.stat(payload.sourceUri);
    if (stat == null) {
      return const TaskFailedPermanent('本地文件已被删除或不可访问');
    }
    if (stat.isDirectory) {
      return const TaskFailedPermanent('file_upload 任务收到了一个文件夹 URI');
    }

    final file = LocalFileEntry(
      uri: payload.sourceUri,
      relativePath: stat.name,
      size: stat.size,
      modifiedAt: stat.modifiedAt,
    );

    await _db.taskDao.updateProgress(task.id, 0, file.size);
    await _service.updatePayload(
      task.id,
      payload.copyWith(
        filesTotal: 1,
        filesDone: 0,
        currentFile: file.relativePath,
      ),
    );

    final adapter = _factory.create(nasConfig);
    final remotePath = RemotePath.join(payload.remoteRoot, file.relativePath);
    var bytesSent = 0;
    try {
      final outcome = await _service.uploadOne(
        adapter: adapter,
        file: file,
        remotePath: remotePath,
        options: payload.options,
        cancel: ctx.shouldCancel,
        pause: ctx.shouldPause,
        onChunkSent: (incr) {
          bytesSent += incr;
          ctx.onProgress(bytesSent, file.size);
        },
      );
      if (outcome == UploadOneOutcome.cancelled) {
        return const TaskCancelledResult();
      }
      if (outcome == UploadOneOutcome.paused) {
        return const TaskPausedResult();
      }
      await _service.updatePayload(
        task.id,
        payload.copyWith(
          filesTotal: 1,
          filesDone: 1,
          currentFile: file.relativePath,
        ),
      );
      if (payload.options.deleteAfterUpload &&
          outcome == UploadOneOutcome.done) {
        final ok = await _service.localSource.delete(file.uri);
        if (!ok) {
          _log.warn(
            'upload',
            '${file.relativePath} 上传成功但本地删除被拒绝（权限或锁定）',
            taskId: task.id,
          );
        }
      }
      return const TaskCompleted();
    } catch (e, st) {
      _log.error('upload', '单文件任务 ${task.id} 失败: $e\n$st', taskId: task.id);
      return TaskFailedTransient(e);
    } finally {
      await adapter.dispose();
    }
  }
}
