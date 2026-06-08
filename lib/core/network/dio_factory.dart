import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../database/app_database.dart';
import 'http_auth_interceptor.dart';
import 'self_signed_cert.dart';

/// 为每个 NAS 配置生成专属的 [Dio] 实例。
///
/// 同一 NAS 复用同一个 Dio（连接池、Digest nonce 缓存均能复用）。
/// AdapterFactory 在创建 [StorageAdapter] 时通过此工厂取 Dio。
class DioFactory {
  DioFactory();

  final Map<String, Dio> _cache = {};

  Dio forNas(NasConfigEntity config) {
    return _cache.putIfAbsent(config.id, () => _build(config));
  }

  void invalidate(String configId) {
    final d = _cache.remove(configId);
    d?.close(force: true);
  }

  void disposeAll() {
    for (final d in _cache.values) {
      d.close(force: true);
    }
    _cache.clear();
  }

  Dio _build(NasConfigEntity config) {
    final base = config.baseUrl.endsWith('/')
        ? config.baseUrl.substring(0, config.baseUrl.length - 1)
        : config.baseUrl;

    final dio = Dio(BaseOptions(
      baseUrl: base,
      connectTimeout: const Duration(seconds: 20),
      sendTimeout: const Duration(minutes: 10),
      receiveTimeout: const Duration(minutes: 10),
      validateStatus: (s) => s != null && s < 500,
      followRedirects: true,
    ));

    if (config.trustSelfSigned) {
      final uri = Uri.tryParse(base);
      if (uri != null && uri.host.isNotEmpty) {
        configureSelfSignedTrust(dio, host: uri.host);
      }
    }

    if (config.username.isNotEmpty || config.passwordEncrypted.isNotEmpty) {
      HttpAuthInterceptor(
        username: config.username,
        password: _decodePassword(config.passwordEncrypted),
      ).attachTo(dio);
    }

    if (kDebugMode) {
      dio.interceptors.add(_DebugLogInterceptor(tag: config.name));
    }

    return dio;
  }

  String _decodePassword(String encoded) {
    // 占位：当前实现为透传。生产应集成 Android Keystore / iOS Keychain。
    return encoded;
  }
}

/// 仅 [kDebugMode] 下打印请求 / 响应 / 错误的拦截器。
/// 凭据（Authorization 头）按 scheme 脱敏，不打印 base64/digest 实际值，
/// 防止用户分享日志时泄漏密码。
class _DebugLogInterceptor extends Interceptor {
  _DebugLogInterceptor({required this.tag});
  final String tag;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final auth = options.headers['Authorization'] as String?;
    final authStr = auth == null
        ? '(none)'
        : '${auth.split(' ').first} <redacted ${auth.length} bytes>';
    debugPrint('[dio:$tag] → ${options.method} ${options.path}  auth=$authStr');
    handler.next(options);
  }

  @override
  void onResponse(Response<dynamic> response, ResponseInterceptorHandler handler) {
    final code = response.statusCode ?? 0;
    debugPrint('[dio:$tag] ← $code ${response.requestOptions.method} ${response.requestOptions.path}');
    // DioFactory 设 validateStatus: < 500 —— 401 / 404 等都走 onResponse 而非 onError，
    // 这里也要打详情，不然 401 看不到 WWW-Authenticate 就排查不下去。
    if (code < 200 || code >= 300) {
      _dumpDetail(response.headers.value('www-authenticate'),
          response.headers.value('content-type'),
          response.data?.toString());
    }
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    final resp = err.response;
    final code = resp?.statusCode;
    debugPrint('[dio:$tag] ✗ ${err.type.name} status=$code ${err.requestOptions.method} ${err.requestOptions.path}');
    _dumpDetail(resp?.headers.value('www-authenticate'),
        resp?.headers.value('content-type'),
        resp?.data?.toString());
    if (err.message != null && err.message!.isNotEmpty) {
      debugPrint('[dio:$tag]   message: ${err.message}');
    }
    handler.next(err);
  }

  void _dumpDetail(String? wwwAuth, String? contentType, String? body) {
    if (wwwAuth != null) debugPrint('[dio:$tag]   WWW-Authenticate: $wwwAuth');
    if (contentType != null) debugPrint('[dio:$tag]   Content-Type: $contentType');
    if (body != null && body.isNotEmpty) {
      final excerpt = body.length > 300 ? '${body.substring(0, 300)}...(${body.length}B total)' : body;
      debugPrint('[dio:$tag]   body: $excerpt');
    }
  }
}
