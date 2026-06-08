import 'dart:async';

import 'package:meta/meta.dart';

import 'adapter_capabilities.dart';

/// 远端目录条目。
@immutable
class RemoteEntry {
  const RemoteEntry({
    required this.name,
    required this.path,
    required this.isDirectory,
    this.size,
    this.modifiedAt,
    this.etag,
  });

  final String name;
  final String path;
  final bool isDirectory;
  final int? size;
  final DateTime? modifiedAt;
  final String? etag;
}

/// 单文件 stat 结果。
@immutable
class RemoteStat {
  const RemoteStat({
    required this.path,
    required this.isDirectory,
    this.size,
    this.modifiedAt,
    this.etag,
  });

  final String path;
  final bool isDirectory;
  final int? size;
  final DateTime? modifiedAt;
  final String? etag;
}

/// 适配器内部使用的取消令牌。
class TransferCancelToken {
  TransferCancelToken();
  final _completer = Completer<void>();
  bool get isCancelled => _completer.isCompleted;
  void cancel() {
    if (!_completer.isCompleted) _completer.complete();
  }
  Future<void> get onCancelled => _completer.future;
}

/// 统一存储适配器抽象。
///
/// 所有协议（WebDAV / SFTP / SMB / Local）实现此接口。
/// 业务模块（upload / file_manager / dedup）只与此抽象交互，
/// 杜绝协议耦合。
abstract class StorageAdapter {
  AdapterCapabilities get capabilities;

  /// 测试连通性 —— 一般实现为对根目录 stat 或 list。
  Future<void> ping();

  Future<List<RemoteEntry>> list(String path);
  Future<RemoteStat?> stat(String path);

  /// 流式下载。[start] / [end] 可选 byte range。
  Stream<List<int>> readStream(String path, {int? start, int? end});

  /// 流式上传。
  ///
  /// [offset] > 0 表示从该偏移续传（适配器必须支持 [AdapterCapabilities.supportsResume]
  /// 时才允许使用，否则应抛错或上层降级为整传）。
  /// [totalLength] 已知时建议附 `Content-Length`。
  Future<void> writeStream(
    String path,
    Stream<List<int>> data, {
    int? totalLength,
    int offset = 0,
    void Function(int sent)? onProgress,
    TransferCancelToken? cancel,
  });

  Future<void> mkdir(String path, {bool recursive = true});
  Future<void> delete(String path);
  Future<void> move(String from, String to);
  Future<void> copy(String from, String to);

  /// 释放底层资源（HTTP 客户端 / SSH 会话等）。
  Future<void> dispose();
}
