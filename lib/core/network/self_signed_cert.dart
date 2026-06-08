import 'dart:io';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';

/// 给信任自签证书的 NAS 创建放行的 HttpClient。
///
/// 仅对配置中标记 `trustSelfSigned=true` 的 host 放行；其他 host
/// 仍走系统标准证书校验。
void configureSelfSignedTrust(Dio dio, {required String host}) {
  final adapter = IOHttpClientAdapter(
    createHttpClient: () {
      final client = HttpClient()
        ..badCertificateCallback = (cert, h, port) => h == host;
      return client;
    },
  );
  dio.httpClientAdapter = adapter;
}
