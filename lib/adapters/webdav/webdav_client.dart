import 'dart:async';

import 'package:dio/dio.dart';

import '../../core/utils/path_utils.dart';
import '../storage_adapter.dart';
import 'propfind_parser.dart';
import 'webdav_errors.dart';

/// 底层 WebDAV HTTP 客户端。
///
/// 与 [WebDavAdapter] 区分：本类是协议直接封装（PROPFIND/PUT/...），
/// adapter 是对外抽象的实现。
class WebDavClient {
  WebDavClient({required this.dio});
  final Dio dio;

  static const _propfindBody = '''<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
    <D:getcontentlength/>
    <D:getlastmodified/>
    <D:getetag/>
    <D:displayname/>
  </D:prop>
</D:propfind>''';

  /// PROPFIND Depth:1 列目录。
  Future<List<RemoteEntry>> list(String path) async {
    final url = _buildUrl(path, trailingSlash: true);
    final resp = await dio.request<String>(
      url,
      data: _propfindBody,
      options: Options(
        method: 'PROPFIND',
        headers: {
          'Depth': '1',
          'Content-Type': 'application/xml; charset=utf-8',
        },
        responseType: ResponseType.plain,
      ),
    );
    _checkStatus(resp, path);
    if (resp.statusCode == 404) throw WebDavNotFound(path);
    return PropfindParser.parse(
      resp.data ?? '',
      selfHref: Uri.parse(url).path,
      stripPrefix: _basePath,
    );
  }

  /// PROPFIND Depth:0 stat 单个资源。返回 null 表示不存在。
  Future<RemoteStat?> stat(String path) async {
    final url = _buildUrl(path);
    try {
      final resp = await dio.request<String>(
        url,
        data: _propfindBody,
        options: Options(
          method: 'PROPFIND',
          headers: {
            'Depth': '0',
            'Content-Type': 'application/xml; charset=utf-8',
          },
          responseType: ResponseType.plain,
        ),
      );
      if (resp.statusCode == 404) return null;
      _checkStatus(resp, path);
      return PropfindParser.parseSingle(resp.data ?? '', stripPrefix: _basePath);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      rethrow;
    }
  }

  /// HEAD —— 用于断点续传前查询已上传字节。返回 (statusCode, contentLength)。
  Future<({int statusCode, int? contentLength})> head(String path) async {
    final url = _buildUrl(path);
    try {
      final resp = await dio.head<void>(
        url,
        options: Options(validateStatus: (s) => s != null && s < 500),
      );
      final len = int.tryParse(resp.headers.value('content-length') ?? '');
      return (statusCode: resp.statusCode ?? 0, contentLength: len);
    } on DioException catch (e) {
      return (statusCode: e.response?.statusCode ?? 0, contentLength: null);
    }
  }

  /// MKCOL（建目录）。返回 true=成功创建 false=已存在。
  Future<bool> mkcol(String path) async {
    final url = _buildUrl(path, trailingSlash: true);
    final resp = await dio.request<void>(
      url,
      options: Options(
        method: 'MKCOL',
        validateStatus: (s) => s != null && s < 500,
      ),
    );
    if (resp.statusCode == 201) return true;
    if (resp.statusCode == 405) return false; // 已存在
    if (resp.statusCode == 409) throw WebDavConflict(path);
    if (resp.statusCode == 401) throw WebDavUnauthorized();
    throw WebDavException('MKCOL 失败', statusCode: resp.statusCode, path: path);
  }

  /// 递归 MKCOL —— 自顶向下创建。
  Future<void> mkcolRecursive(String path) async {
    final n = RemotePath.normalize(path);
    if (n == '/') return;
    final segments = n.split('/').where((s) => s.isNotEmpty).toList();
    var current = '';
    for (final seg in segments) {
      current = '$current/$seg';
      try {
        await mkcol(current);
      } on WebDavConflict {
        // 父目录都自顶向下创建，理论不应触发；遇到就忽略，继续往下。
      }
    }
  }

  /// PUT 单段上传。[offset] > 0 时附 Content-Range（需服务端支持）。
  Future<void> put({
    required String path,
    required Stream<List<int>> data,
    required int? contentLength,
    int offset = 0,
    int? totalLength,
    void Function(int sent, int? total)? onProgress,
    CancelToken? cancelToken,
  }) async {
    final url = _buildUrl(path);
    final headers = <String, dynamic>{};
    if (contentLength != null) headers['Content-Length'] = contentLength;
    if (offset > 0 && totalLength != null) {
      headers['Content-Range'] = 'bytes $offset-${totalLength - 1}/$totalLength';
    }

    final resp = await dio.put<void>(
      url,
      data: data,
      options: Options(
        headers: headers,
        validateStatus: (s) => s != null && s < 500,
        contentType: 'application/octet-stream',
      ),
      onSendProgress: (sent, total) => onProgress?.call(sent, total < 0 ? null : total),
      cancelToken: cancelToken,
    );

    final code = resp.statusCode ?? 0;
    if (code == 200 || code == 201 || code == 204) return;
    if (code == 401) throw WebDavUnauthorized();
    if (code == 409) throw WebDavConflict(path);
    throw WebDavException('PUT 失败', statusCode: code, path: path);
  }

  Future<void> delete(String path) async {
    final url = _buildUrl(path);
    final resp = await dio.delete<void>(
      url,
      options: Options(validateStatus: (s) => s != null && s < 500),
    );
    final code = resp.statusCode ?? 0;
    if (code == 200 || code == 204 || code == 404) return;
    if (code == 401) throw WebDavUnauthorized();
    throw WebDavException('DELETE 失败', statusCode: code, path: path);
  }

  Future<void> move(String from, String to, {bool overwrite = false}) async {
    final fromUrl = _buildUrl(from);
    final destUrl = _buildUrl(to);
    final resp = await dio.request<void>(
      fromUrl,
      options: Options(
        method: 'MOVE',
        headers: {
          'Destination': destUrl,
          'Overwrite': overwrite ? 'T' : 'F',
        },
        validateStatus: (s) => s != null && s < 500,
      ),
    );
    final code = resp.statusCode ?? 0;
    if (code == 201 || code == 204) return;
    throw WebDavException('MOVE 失败', statusCode: code, path: from);
  }

  Future<void> copy(String from, String to, {bool overwrite = false}) async {
    final fromUrl = _buildUrl(from);
    final destUrl = _buildUrl(to);
    final resp = await dio.request<void>(
      fromUrl,
      options: Options(
        method: 'COPY',
        headers: {
          'Destination': destUrl,
          'Overwrite': overwrite ? 'T' : 'F',
        },
        validateStatus: (s) => s != null && s < 500,
      ),
    );
    final code = resp.statusCode ?? 0;
    if (code == 201 || code == 204) return;
    throw WebDavException('COPY 失败', statusCode: code, path: from);
  }

  /// GET 下载（流式）。
  Stream<List<int>> get(String path, {int? start, int? end}) async* {
    final url = _buildUrl(path);
    final headers = <String, dynamic>{};
    if (start != null || end != null) {
      final s = start ?? 0;
      final e = end == null ? '' : end.toString();
      headers['Range'] = 'bytes=$s-$e';
    }
    final resp = await dio.get<ResponseBody>(
      url,
      options: Options(
        headers: headers,
        responseType: ResponseType.stream,
        validateStatus: (s) => s != null && s < 500,
      ),
    );
    final code = resp.statusCode ?? 0;
    if (code == 404) throw WebDavNotFound(path);
    if (code == 401) throw WebDavUnauthorized();
    if (code >= 300) {
      throw WebDavException('GET 失败', statusCode: code, path: path);
    }
    yield* resp.data!.stream;
  }

  void _checkStatus(Response<dynamic> resp, String path) {
    final code = resp.statusCode ?? 0;
    if (code == 401) throw WebDavUnauthorized();
    if (code == 404) throw WebDavNotFound(path);
    if (code >= 400) {
      throw WebDavException('请求失败', statusCode: code, path: path);
    }
  }

  /// 拼出完整 URL —— 显式串接 baseUrl + 编码后的 path，避免 Dio 内部走
  /// RFC 3986 URI resolve：当 baseUrl 含路径前缀（如 `http://h:5005/webdav`）
  /// 时，一个以 `/` 开头的相对路径会**替换**整个 baseUrl 的 path，导致请求
  /// 发到 `http://h:5005/`，丢掉 webdav 服务前缀。
  String _buildUrl(String path, {bool trailingSlash = false}) {
    final base = dio.options.baseUrl;
    final trimmedBase = base.endsWith('/') ? base.substring(0, base.length - 1) : base;
    final encoded = RemotePath.encode(path);
    final suffix = encoded == '/' ? '' : encoded;
    var url = '$trimmedBase$suffix';
    if (trailingSlash && !url.endsWith('/')) url = '$url/';
    return url;
  }

  /// baseUrl 中的 path 前缀（不含末尾 `/`）。
  /// 例如 `http://h:5005/webdav` → `/webdav`；`http://h:5005` → `''`。
  /// 给 PropfindParser 用于从服务端 href 上剥掉前缀。
  String get _basePath {
    final uri = Uri.tryParse(dio.options.baseUrl);
    final p = uri?.path ?? '';
    if (p.isEmpty || p == '/') return '';
    return p.endsWith('/') ? p.substring(0, p.length - 1) : p;
  }
}
