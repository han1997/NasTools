import 'package:freezed_annotation/freezed_annotation.dart';

import 'upload_options.dart';

part 'upload_task.freezed.dart';
part 'upload_task.g.dart';

/// Upload 模块的任务 payload —— 写入 [Tasks.payloadJson]。
///
/// 单个 Drift Task 对应一次"文件夹上传" —— 文件夹下的文件由 executor 在
/// 运行时枚举（不预先展开到数据库）。chunk 状态写 `task_chunks` 表。
@freezed
class UploadTaskPayload with _$UploadTaskPayload {
  const factory UploadTaskPayload({
    required String sourceUri, // SAF tree URI（content://...）或本地路径
    required String sourceKind, // 'saf' | 'file'
    required String remoteRoot, // 目标 NAS 上的根目录
    required UploadOptions options,
    @Default(0) int filesTotal,
    @Default(0) int filesDone,
    @Default(0) int filesFailed,
    String? currentFile,
  }) = _UploadTaskPayload;

  factory UploadTaskPayload.fromJson(Map<String, dynamic> json) =>
      _$UploadTaskPayloadFromJson(json);
}
