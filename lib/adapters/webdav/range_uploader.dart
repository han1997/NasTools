import 'dart:async';

import 'package:dio/dio.dart';

import 'webdav_client.dart';

/// 带断点续传探测的上传辅助。
///
/// 流程：
/// 1. HEAD 目标 path —— 拿到 contentLength = 已存在字节数。
/// 2. 如果 contentLength == totalLength：跳过（远端文件等同源）。
/// 3. 如果 contentLength > 0 且 < totalLength：尝试探测 Content-Range 支持
///    —— 直接走 [WebDavClient.put] + Content-Range 即可，服务端不支持会返 5xx；
///    本类不预先探测，而是直接尝试，失败由调用方降级。
class RangeUploader {
  RangeUploader(this.client);
  final WebDavClient client;

  /// 探测已有字节。返回 (existsAlready, existingLength)。
  Future<({bool existsAlready, int existingLength})> probe({
    required String path,
    required int totalLength,
  }) async {
    final head = await client.head(path);
    if (head.statusCode == 404) {
      return (existsAlready: false, existingLength: 0);
    }
    final len = head.contentLength ?? 0;
    if (len >= totalLength) {
      return (existsAlready: true, existingLength: totalLength);
    }
    return (existsAlready: false, existingLength: len);
  }

  /// 尝试以 offset 续传单文件。
  /// 若服务端不支持 Content-Range，调用方应捕获 [Exception] 后降级到整传。
  Future<void> resume({
    required String path,
    required Stream<List<int>> data,
    required int offset,
    required int totalLength,
    void Function(int sent, int? total)? onProgress,
    CancelToken? cancelToken,
  }) {
    return client.put(
      path: path,
      data: data,
      contentLength: totalLength - offset,
      offset: offset,
      totalLength: totalLength,
      onProgress: onProgress,
      cancelToken: cancelToken,
    );
  }
}
