import 'package:freezed_annotation/freezed_annotation.dart';

part 'upload_options.freezed.dart';
part 'upload_options.g.dart';

/// 冲突处理策略。
enum OverwriteMode {
  /// 远端已存在则跳过。
  skip,

  /// 远端已存在则覆盖。
  overwrite,

  /// 若远端字节较少则尝试续传，相等则跳过，否则覆盖。
  resumeOrOverwrite,

  /// 远端已存在则上传到 `name_1.ext` / `name_2.ext` 等首个不冲突的名字。
  rename,
}

@freezed
class UploadOptions with _$UploadOptions {
  const factory UploadOptions({
    @Default(8 * 1024 * 1024) int chunkSizeBytes,
    @Default(OverwriteMode.resumeOrOverwrite) OverwriteMode overwriteMode,
    @Default(false) bool wifiOnly,
    @Default(true) bool preserveStructure,

    /// 文件名 basename 白名单正则。null / 空串 = 不过滤；目录不参与正则匹配，
    /// 总是被递归进入（否则会丢掉里面的可匹配文件）。
    String? filterRegex,

    /// 单文件上传成功（含续传完成 / rename 后入库）即调 LocalSource.delete
    /// 删本地。skip 不算"上传过"，不删。
    @Default(false) bool deleteAfterUpload,
  }) = _UploadOptions;

  factory UploadOptions.fromJson(Map<String, dynamic> json) =>
      _$UploadOptionsFromJson(json);
}
