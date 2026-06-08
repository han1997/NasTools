import 'dart:async';
import 'dart:convert';

import '../../../adapters/adapter_factory.dart';
import '../../../core/database/app_database.dart';
import '../../../core/logger/app_logger.dart';
import '../../../core/task/task_executor.dart';
import '../../../core/utils/path_utils.dart';
import '../domain/upload_task.dart';
import '../domain/upload_task_type.dart';
import '../service/upload_service.dart';

/// folder_upload 任务的执行器。
///
/// 流程：
/// 1. 反序列化 payload —— 得到本地根 URI、远端根目录、options
/// 2. summary 一次得到总字节数 —— 写回 task.totalBytes
/// 3. 遍历文件夹，对每个文件调用 [UploadService.uploadOne]
/// 4. 全部完成 → TaskCompleted
class UploadTaskExecutor implements TaskExecutor {
  UploadTaskExecutor({
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
  String get type => UploadTaskType.folderUpload;

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

    final adapter = _factory.create(nasConfig);
    try {
      final summary = await _service.summarize(
        payload.sourceUri,
        filterRegex: payload.options.filterRegex,
      );
      await _db.taskDao.updateProgress(task.id, 0, summary.bytesTotal);
      await _service.updatePayload(
        task.id,
        payload.copyWith(filesTotal: summary.filesTotal),
      );

      _log.info(
        'upload',
        '任务 ${task.id} 开始：${summary.filesTotal} 文件 / ${summary.bytesTotal} B',
        taskId: task.id,
      );

      var done = 0;
      var failed = 0;
      var bytesSent = 0;

      await for (final file in _service.walk(
        payload.sourceUri,
        filterRegex: payload.options.filterRegex,
      )) {
        if (ctx.shouldCancel()) return const TaskCancelledResult();
        if (ctx.shouldPause()) return const TaskPausedResult();

        await _service.updatePayload(
          task.id,
          payload.copyWith(
            filesTotal: summary.filesTotal,
            filesDone: done,
            filesFailed: failed,
            currentFile: file.relativePath,
          ),
        );

        final remotePath =
            RemotePath.join(payload.remoteRoot, file.relativePath);
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
              ctx.onProgress(bytesSent, summary.bytesTotal);
            },
          );
          if (outcome == UploadOneOutcome.cancelled) {
            return const TaskCancelledResult();
          }
          if (outcome == UploadOneOutcome.paused) {
            return const TaskPausedResult();
          }
          done++;
          // skip 不算上传过，不删本地；done / done-via-resume / rename 都算成功
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
        } catch (e, st) {
          failed++;
          _log.error('upload', '文件 ${file.relativePath} 失败: $e\n$st',
              taskId: task.id);
        }
      }

      await _service.updatePayload(
        task.id,
        payload.copyWith(
          filesTotal: summary.filesTotal,
          filesDone: done,
          filesFailed: failed,
        ),
      );

      if (failed > 0 && done == 0) {
        return TaskFailedTransient('所有文件失败 ($failed)');
      }
      return const TaskCompleted();
    } catch (e, st) {
      _log.error('upload', '任务 ${task.id} 异常: $e\n$st', taskId: task.id);
      return TaskFailedTransient(e);
    } finally {
      await adapter.dispose();
    }
  }
}
