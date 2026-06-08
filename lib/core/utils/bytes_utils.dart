/// 字节数格式化。
class BytesFormat {
  BytesFormat._();

  static const _units = ['B', 'KB', 'MB', 'GB', 'TB'];

  static String human(int bytes, {int fractionDigits = 1}) {
    if (bytes <= 0) return '0 B';
    var size = bytes.toDouble();
    var u = 0;
    while (size >= 1024 && u < _units.length - 1) {
      size /= 1024;
      u++;
    }
    return '${size.toStringAsFixed(u == 0 ? 0 : fractionDigits)} ${_units[u]}';
  }

  /// 速率格式 e.g. "1.2 MB/s"
  static String rate(int bytesPerSec) {
    if (bytesPerSec <= 0) return '0 B/s';
    return '${human(bytesPerSec)}/s';
  }

  /// 预估剩余时间，秒数。
  static String eta(int remainingBytes, int bytesPerSec) {
    if (bytesPerSec <= 0 || remainingBytes <= 0) return '--';
    final s = (remainingBytes / bytesPerSec).round();
    if (s < 60) return '${s}s';
    if (s < 3600) return '${s ~/ 60}m${s % 60}s';
    return '${s ~/ 3600}h${(s % 3600) ~/ 60}m';
  }
}
