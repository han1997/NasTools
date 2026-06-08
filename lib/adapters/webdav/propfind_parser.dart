import 'package:xml/xml.dart';

import '../storage_adapter.dart';

/// 解析 WebDAV PROPFIND 的 multistatus 响应。
///
/// 支持以下命名空间习惯：
/// * `DAV:` 默认（D 前缀或不带前缀）
/// * 群晖 / Nextcloud 返回的 `oc:` / `s:` 额外字段忽略
class PropfindParser {
  PropfindParser._();

  /// 解析为远端条目列表。
  ///
  /// [selfHref] PROPFIND 请求自身的 URL path —— 会被排除。
  /// [stripPrefix] baseUrl 的 path 前缀（如 `/webdav`）——
  /// 从服务端返回的 href 上剥掉后再放入 [RemoteEntry.path]，让后续 list/stat
  /// 调用传入的 path 始终相对于 webdav 服务根，避免前缀被双重拼接。
  static List<RemoteEntry> parse(
    String body, {
    required String selfHref,
    String stripPrefix = '',
  }) {
    final doc = XmlDocument.parse(body);
    final responses = doc.findAllElements('response', namespace: '*');

    final selfNorm = _normalize(Uri.decodeFull(_pathOnly(selfHref)));

    final result = <RemoteEntry>[];
    for (final r in responses) {
      final href = _text(r, 'href');
      if (href == null) continue;

      final hrefPath = Uri.decodeFull(_pathOnly(href));

      // 排除自身（PROPFIND Depth:1 会把当前目录也返回）。
      // 服务端可能返回绝对 URL（http://host/webdav/foo/）或绝对 path
      // （/webdav/foo/），统一抽 path 段比对。
      if (_normalize(hrefPath) == selfNorm) continue;

      final name = _basename(hrefPath);
      if (name.isEmpty) continue;

      final propstat = r.findElements('propstat', namespace: '*').firstOrNull;
      if (propstat == null) continue;
      final prop = propstat.findElements('prop', namespace: '*').firstOrNull;
      if (prop == null) continue;

      final isCollection = prop
              .findElements('resourcetype', namespace: '*')
              .firstOrNull
              ?.findElements('collection', namespace: '*')
              .isNotEmpty ??
          false;

      final sizeStr = _text(prop, 'getcontentlength');
      final modifiedStr = _text(prop, 'getlastmodified');
      final etag = _text(prop, 'getetag');

      result.add(RemoteEntry(
        name: name,
        path: _pathFromHref(hrefPath, stripPrefix: stripPrefix),
        isDirectory: isCollection,
        size: int.tryParse(sizeStr ?? ''),
        modifiedAt: _parseHttpDate(modifiedStr),
        etag: etag,
      ));
    }
    return result;
  }

  /// 解析单条 stat（PROPFIND Depth: 0）。
  static RemoteStat? parseSingle(String body, {String stripPrefix = ''}) {
    final doc = XmlDocument.parse(body);
    final response = doc.findAllElements('response', namespace: '*').firstOrNull;
    if (response == null) return null;
    final href = _text(response, 'href');
    if (href == null) return null;

    final prop = response
        .findElements('propstat', namespace: '*')
        .firstOrNull
        ?.findElements('prop', namespace: '*')
        .firstOrNull;
    if (prop == null) return null;

    final isCollection = prop
            .findElements('resourcetype', namespace: '*')
            .firstOrNull
            ?.findElements('collection', namespace: '*')
            .isNotEmpty ??
        false;

    return RemoteStat(
      path: _pathFromHref(Uri.decodeFull(_pathOnly(href)), stripPrefix: stripPrefix),
      isDirectory: isCollection,
      size: int.tryParse(_text(prop, 'getcontentlength') ?? ''),
      modifiedAt: _parseHttpDate(_text(prop, 'getlastmodified')),
      etag: _text(prop, 'getetag'),
    );
  }

  static String? _text(XmlElement e, String name) {
    final el = e.findElements(name, namespace: '*').firstOrNull;
    return el?.innerText.trim();
  }

  static String _basename(String href) {
    final clean = href.endsWith('/') ? href.substring(0, href.length - 1) : href;
    final i = clean.lastIndexOf('/');
    return i < 0 ? clean : clean.substring(i + 1);
  }

  static String _normalize(String href) {
    var h = href;
    if (h.endsWith('/')) h = h.substring(0, h.length - 1);
    return h;
  }

  /// 抽 URI 的 path 段；输入若本身就是相对 path 则原样返回。
  static String _pathOnly(String hrefOrUrl) {
    final uri = Uri.tryParse(hrefOrUrl);
    if (uri != null && uri.hasScheme) return uri.path;
    return hrefOrUrl;
  }

  static String _pathFromHref(String href, {String stripPrefix = ''}) {
    // href 此处已是 path-only（_pathOnly 处理过）。去末尾 `/`，并剥前缀。
    var path = href.endsWith('/') && href.length > 1
        ? href.substring(0, href.length - 1)
        : href;
    if (stripPrefix.isNotEmpty && stripPrefix != '/') {
      final trimPrefix = stripPrefix.endsWith('/')
          ? stripPrefix.substring(0, stripPrefix.length - 1)
          : stripPrefix;
      if (path == trimPrefix) {
        path = '/';
      } else if (path.startsWith('$trimPrefix/')) {
        path = path.substring(trimPrefix.length);
      }
    }
    return path;
  }

  static DateTime? _parseHttpDate(String? s) {
    if (s == null || s.isEmpty) return null;
    try {
      return HttpDateParser.parse(s);
    } catch (_) {
      return null;
    }
  }
}

extension _FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull {
    final it = iterator;
    if (it.moveNext()) return it.current;
    return null;
  }
}

/// 简易 RFC 1123 日期解析（Mon, 17 May 2026 12:34:56 GMT）。
class HttpDateParser {
  HttpDateParser._();

  static DateTime parse(String input) {
    final s = input.trim();
    final parts = s.split(RegExp(r'\s+'));
    if (parts.length < 6) {
      // 退而求其次 —— 让 DateTime.tryParse 处理 ISO。
      final iso = DateTime.tryParse(s);
      if (iso != null) return iso;
      throw FormatException('无法解析日期: $s');
    }
    // [0]=DayOfWeek (Mon,) ignore
    final day = int.parse(parts[1]);
    final month = _monthIndex(parts[2]);
    final year = int.parse(parts[3]);
    final hms = parts[4].split(':');
    final hh = int.parse(hms[0]);
    final mm = int.parse(hms[1]);
    final ss = int.parse(hms[2]);
    return DateTime.utc(year, month, day, hh, mm, ss);
  }

  static int _monthIndex(String m) {
    const map = {
      'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6,
      'Jul': 7, 'Aug': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Dec': 12,
    };
    final v = map[m];
    if (v == null) throw FormatException('未知月份: $m');
    return v;
  }
}
