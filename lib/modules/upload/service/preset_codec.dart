import 'dart:convert';

import 'package:drift/drift.dart' show Value;

import '../../../core/database/app_database.dart';
import '../domain/upload_options.dart';

/// 预设字段 ↔ UploadOptions / UploadPresetsCompanion 之间的转换工具。
///
/// 把 `presets/edit_page.dart` 和 `upload_launch_page.dart` 共用的"如何从一组
/// UI 字段构造一个可 upsert 的 companion"逻辑收口于此，避免两处实现漂移。
class PresetCodec {
  PresetCodec._();

  /// 解码持久化的 optionsJson；任何错误吞掉返回 null（由调用方决定兜底值）。
  static UploadOptions? decode(String json) {
    if (json.isEmpty) return null;
    try {
      return UploadOptions.fromJson(jsonDecode(json) as Map<String, dynamic>);
    } catch (_) {
      return null;
    }
  }

  /// 由 UI 字段构造一个 Drift Companion。
  ///
  /// 新建预设时 `id` 传 null —— DAO 的 upsert 会自动 uuid v4；编辑现有预设
  /// 传原 id 即可。
  static UploadPresetsCompanion buildCompanion({
    String? id,
    required String name,
    required String nasConfigId,
    required String localUri,
    required String localLabel,
    required String remoteRoot,
    required UploadOptions options,
  }) {
    return UploadPresetsCompanion(
      id: id == null ? const Value.absent() : Value(id),
      name: Value(name.trim()),
      nasConfigId: Value(nasConfigId),
      localUri: Value(localUri),
      localLabel: Value(localLabel),
      remoteRoot: Value(remoteRoot.trim().isEmpty ? '/' : remoteRoot.trim()),
      optionsJson: Value(jsonEncode(options.toJson())),
    );
  }
}
