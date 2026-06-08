import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

/// 文件 / 内容哈希工具。
class HashUtils {
  HashUtils._();

  static String sha256OfString(String s) {
    return sha256.convert(utf8.encode(s)).toString();
  }

  /// 流式计算文件 SHA-256（避免一次性读入内存）。
  static Future<String> sha256OfFile(File f) async {
    final digest = await sha256.bind(f.openRead()).first;
    return digest.toString();
  }
}
