/// 远端路径拼接与规范化工具（POSIX 风格）。
class RemotePath {
  RemotePath._();

  /// 规范化 —— 去掉重复 `/`，确保以 `/` 开头，去掉末尾 `/`（根除外）。
  static String normalize(String path) {
    if (path.isEmpty) return '/';
    var p = path.replaceAll(RegExp(r'/+'), '/');
    if (!p.startsWith('/')) p = '/$p';
    if (p.length > 1 && p.endsWith('/')) p = p.substring(0, p.length - 1);
    return p;
  }

  /// 拼接多段路径。
  static String join(String a, [String? b, String? c, String? d]) {
    final parts = [a, b, c, d].whereType<String>().where((s) => s.isNotEmpty);
    return normalize(parts.join('/'));
  }

  /// 父目录。
  static String parent(String path) {
    final n = normalize(path);
    if (n == '/') return '/';
    final i = n.lastIndexOf('/');
    if (i <= 0) return '/';
    return n.substring(0, i);
  }

  /// 末尾的 basename。
  static String basename(String path) {
    final n = normalize(path);
    if (n == '/') return '';
    final i = n.lastIndexOf('/');
    return n.substring(i + 1);
  }

  /// basename 的扩展名（含点，全小写）。无扩展名返回空串。
  ///
  /// 隐藏文件（如 `.gitignore`、`Makefile`）视为无扩展。多级扩展取最后一段
  /// （`a.tar.gz` → `.gz`），与 MIME 推断的实际需要吻合。
  static String extension(String path) {
    final base = basename(path);
    final i = base.lastIndexOf('.');
    if (i <= 0) return '';
    return base.substring(i).toLowerCase();
  }

  /// 把 path 各段做 URI 编码（保留 `/` 不变）。
  static String encode(String path) {
    final n = normalize(path);
    return n
        .split('/')
        .map((seg) => seg.isEmpty ? '' : Uri.encodeComponent(seg))
        .join('/');
  }
}
