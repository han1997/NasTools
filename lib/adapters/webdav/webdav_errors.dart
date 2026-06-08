/// WebDAV 错误类型。
class WebDavException implements Exception {
  WebDavException(this.message, {this.statusCode, this.path});
  final String message;
  final int? statusCode;
  final String? path;

  @override
  String toString() {
    final s = statusCode == null ? '' : ' [HTTP $statusCode]';
    final p = path == null ? '' : ' path=$path';
    return 'WebDavException$s$p: $message';
  }
}

class WebDavNotFound extends WebDavException {
  WebDavNotFound(String path) : super('资源不存在', statusCode: 404, path: path);
}

class WebDavUnauthorized extends WebDavException {
  WebDavUnauthorized() : super('鉴权失败', statusCode: 401);
}

class WebDavConflict extends WebDavException {
  WebDavConflict(String path) : super('冲突（路径已存在或父目录缺失）', statusCode: 409, path: path);
}
