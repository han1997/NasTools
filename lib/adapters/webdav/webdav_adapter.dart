import 'dart:async';

import 'package:dio/dio.dart';

import '../adapter_capabilities.dart';
import '../storage_adapter.dart';
import 'range_uploader.dart';
import 'webdav_client.dart';
import 'webdav_errors.dart';

/// WebDAV 实现的 [StorageAdapter]。
class WebDavAdapter implements StorageAdapter {
  WebDavAdapter({required Dio dio, required this.baseUrl})
      : _client = WebDavClient(dio: dio) {
    _ranger = RangeUploader(_client);
  }

  final String baseUrl;
  final WebDavClient _client;
  late final RangeUploader _ranger;

  @override
  AdapterCapabilities get capabilities => const AdapterCapabilities(
        supportsResume: true,
        supportsRange: true,
        supportsMove: true,
        supportsCopy: true,
        supportsChecksum: false,
        maxConcurrentTransfers: 2,
      );

  @override
  Future<void> ping() async {
    await _client.list('/');
  }

  @override
  Future<List<RemoteEntry>> list(String path) => _client.list(path);

  @override
  Future<RemoteStat?> stat(String path) => _client.stat(path);

  @override
  Stream<List<int>> readStream(String path, {int? start, int? end}) {
    return _client.get(path, start: start, end: end);
  }

  @override
  Future<void> writeStream(
    String path,
    Stream<List<int>> data, {
    int? totalLength,
    int offset = 0,
    void Function(int sent)? onProgress,
    TransferCancelToken? cancel,
  }) async {
    final cancelToken = _bridge(cancel);

    if (offset > 0 && totalLength != null) {
      try {
        await _ranger.resume(
          path: path,
          data: data,
          offset: offset,
          totalLength: totalLength,
          onProgress: (sent, _) => onProgress?.call(sent + offset),
          cancelToken: cancelToken,
        );
        return;
      } on WebDavException catch (e) {
        // 续传失败（多数情况是服务端不支持 Content-Range）。抛出可识别异常，
        // 由 executor 重新构造 offset=0 的流后整传 —— 这里无法自我恢复，
        // 因为传入的 [data] 已经从 offset 开始读了，无法回退。
        throw ResumeNotSupported(e);
      }
    }

    await _client.put(
      path: path,
      data: data,
      contentLength: totalLength,
      offset: 0,
      totalLength: totalLength,
      onProgress: (sent, _) => onProgress?.call(sent),
      cancelToken: cancelToken,
    );
  }

  @override
  Future<void> mkdir(String path, {bool recursive = true}) {
    if (recursive) return _client.mkcolRecursive(path);
    return _client.mkcol(path).then((_) => null);
  }

  @override
  Future<void> delete(String path) => _client.delete(path);

  @override
  Future<void> move(String from, String to) => _client.move(from, to);

  @override
  Future<void> copy(String from, String to) => _client.copy(from, to);

  @override
  Future<void> dispose() async {
    // dio 由 DioFactory 管理，这里不主动关闭。
  }

  CancelToken? _bridge(TransferCancelToken? token) {
    if (token == null) return null;
    final ct = CancelToken();
    token.onCancelled.then((_) {
      if (!ct.isCancelled) ct.cancel('user requested');
    });
    return ct;
  }
}

class ResumeNotSupported implements Exception {
  ResumeNotSupported(this.cause);
  final WebDavException cause;
  @override
  String toString() => '断点续传被服务端拒绝: $cause';
}
