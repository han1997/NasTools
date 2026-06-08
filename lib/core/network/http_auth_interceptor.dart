import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

/// 同时支持 HTTP Basic 与 Digest 鉴权的拦截器。
///
/// 策略：
/// 1. 默认请求不附 Authorization。
/// 2. 收到 401 时读 `WWW-Authenticate`：
///    - `Basic` → 附 base64(user:pass) 重试一次。
///    - `Digest` → 解析 nonce/qop，计算响应，附 Authorization 重试一次。
/// 3. Digest 的 nonce 在 [_digestState] 缓存，后续请求直接附用，避免每次先 401。
///
/// 关键设计：重试必须复用父 [Dio] 实例 —— 否则会丢失自签证书放行、其它拦截器、
/// 连接池等。父 Dio 在 [attachTo] 时注入。
class HttpAuthInterceptor extends Interceptor {
  HttpAuthInterceptor({required this.username, required this.password});

  final String username;
  final String password;

  Dio? _dio;

  _DigestState? _digestState;

  /// 把拦截器挂到指定 [Dio] 上，并在内部持有引用以便重试时复用。
  void attachTo(Dio dio) {
    _dio = dio;
    dio.interceptors.add(this);
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    // 若已经有缓存的 Digest 状态，预附 Authorization。
    if (_digestState != null && !options.headers.containsKey('Authorization')) {
      options.headers['Authorization'] =
          _buildDigestHeader(_digestState!, options.method, _digestUri(options));
    }
    handler.next(options);
  }

  @override
  Future<void> onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) async {
    // DioFactory 设了 validateStatus: < 500，所以 401 会落到 onResponse 而非 onError。
    // 我们必须在这里拦截重试，否则鉴权 401 会原样穿透到调用方。
    if (response.statusCode != 401) {
      handler.next(response);
      return;
    }
    final retried = await _retryWithAuth(response, response.requestOptions);
    if (retried != null) {
      handler.resolve(retried);
    } else {
      handler.next(response);
    }
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    // 兜底：若上层有更严格的 validateStatus，401 走的是 onError。
    final resp = err.response;
    if (resp?.statusCode != 401) {
      handler.next(err);
      return;
    }
    final retried = await _retryWithAuth(resp!, err.requestOptions);
    if (retried != null) {
      handler.resolve(retried);
    } else {
      handler.next(err);
    }
  }

  /// 用 WWW-Authenticate 指定的方案补凭据后用父 Dio 重发。
  /// 成功（含再次 401，由调用方自己判定）返回新响应；放弃重试返回 null。
  Future<Response<dynamic>?> _retryWithAuth(
    Response<dynamic> response,
    RequestOptions req,
  ) async {
    final authHeader = response.headers.value('www-authenticate');
    if (authHeader == null) {
      if (kDebugMode) debugPrint('[auth] 401 但无 WWW-Authenticate，放弃');
      return null;
    }
    if (req.extra['__retriedAuth'] == true) {
      if (kDebugMode) debugPrint('[auth] 已重试过仍 401（凭据/权限错误），放弃');
      return null;
    }
    req.extra['__retriedAuth'] = true;

    final parent = _dio;
    if (parent == null) {
      if (kDebugMode) debugPrint('[auth] 父 Dio 未注入，放弃');
      return null;
    }

    try {
      if (authHeader.toLowerCase().startsWith('basic')) {
        req.headers['Authorization'] = _basicHeader();
        if (kDebugMode) {
          debugPrint('[auth] retry with Basic (user=$username, pw=${password.length}B)');
        }
      } else if (authHeader.toLowerCase().startsWith('digest')) {
        _digestState = _parseDigestChallenge(authHeader);
        req.headers['Authorization'] =
            _buildDigestHeader(_digestState!, req.method, _digestUri(req));
        if (kDebugMode) {
          debugPrint('[auth] retry with Digest (user=$username, '
              'realm=${_digestState!.realm}, qop=${_digestState!.qop})');
        }
      } else {
        if (kDebugMode) debugPrint('[auth] 不支持的 scheme: $authHeader');
        return null;
      }
      // 用父 Dio 重发 —— 保留自签证书 / 其他拦截器 / 连接池。
      final r = await parent.fetch<dynamic>(req);
      if (kDebugMode) debugPrint('[auth] retry result: ${r.statusCode}');
      return r;
    } catch (e) {
      if (kDebugMode) debugPrint('[auth] retry threw: $e');
      return null;
    }
  }

  /// 计算 Digest 哈希用的 URI —— RFC 2617 要求是 Request-URI 的 path 段，
  /// 不能是完整 URL。WebDavClient 现在传完整 URL 给 Dio，
  /// 这里需要把 path 段挑出来。
  String _digestUri(RequestOptions options) {
    final path = options.path;
    final uri = Uri.tryParse(path);
    if (uri != null && uri.hasScheme) {
      final p = uri.path;
      // 把 query 也带上（Digest 哈希含完整 Request-URI）。
      return uri.query.isEmpty ? p : '$p?${uri.query}';
    }
    return path;
  }

  String _basicHeader() {
    final creds = base64Encode(utf8.encode('$username:$password'));
    return 'Basic $creds';
  }

  _DigestState _parseDigestChallenge(String header) {
    final params = <String, String>{};
    final body = header.substring(header.indexOf(' ') + 1);
    final re = RegExp(r'(\w+)\s*=\s*("([^"]*)"|([^,]+))');
    for (final m in re.allMatches(body)) {
      final key = m.group(1)!.toLowerCase();
      final val = m.group(3) ?? m.group(4) ?? '';
      params[key] = val.trim();
    }
    return _DigestState(
      realm: params['realm'] ?? '',
      nonce: params['nonce'] ?? '',
      qop: params['qop'] ?? '',
      algorithm: (params['algorithm'] ?? 'MD5').toUpperCase(),
      opaque: params['opaque'],
    );
  }

  String _buildDigestHeader(_DigestState s, String method, String uri) {
    final cnonce = _generateNonce();
    s.nc += 1;
    final nc = s.nc.toRadixString(16).padLeft(8, '0');

    final ha1Input = '$username:${s.realm}:$password';
    final ha1 = md5.convert(utf8.encode(ha1Input)).toString();
    final ha2 = md5.convert(utf8.encode('$method:$uri')).toString();

    final response = s.qop.isNotEmpty
        ? md5.convert(utf8.encode('$ha1:${s.nonce}:$nc:$cnonce:${s.qop}:$ha2')).toString()
        : md5.convert(utf8.encode('$ha1:${s.nonce}:$ha2')).toString();

    final parts = <String>[
      'username="$username"',
      'realm="${s.realm}"',
      'nonce="${s.nonce}"',
      'uri="$uri"',
      'algorithm=${s.algorithm}',
      'response="$response"',
    ];
    if (s.qop.isNotEmpty) {
      parts.addAll([
        'qop=${s.qop}',
        'nc=$nc',
        'cnonce="$cnonce"',
      ]);
    }
    if (s.opaque != null) parts.add('opaque="${s.opaque}"');
    return 'Digest ${parts.join(', ')}';
  }

  String _generateNonce() {
    final r = Random.secure();
    final bytes = List<int>.generate(8, (_) => r.nextInt(256));
    return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }
}

class _DigestState {
  _DigestState({
    required this.realm,
    required this.nonce,
    required this.qop,
    required this.algorithm,
    this.opaque,
  });

  final String realm;
  final String nonce;
  final String qop;
  final String algorithm;
  final String? opaque;
  int nc = 0;
}
