import 'package:meta/meta.dart';

/// 协议适配器能力声明。
@immutable
class AdapterCapabilities {
  const AdapterCapabilities({
    this.supportsResume = false,
    this.supportsRange = false,
    this.supportsMove = false,
    this.supportsCopy = false,
    this.supportsChecksum = false,
    this.maxConcurrentTransfers = 4,
  });

  /// 是否支持基于已有字节的续传（HEAD + Content-Range）。
  final bool supportsResume;

  /// 是否支持下载时的 byte range 请求。
  final bool supportsRange;

  /// 是否支持服务端 MOVE / RENAME。
  final bool supportsMove;

  /// 是否支持服务端 COPY。
  final bool supportsCopy;

  /// 是否提供文件校验信息（ETag / 哈希）。
  final bool supportsChecksum;

  /// 单连接建议最大并发。
  final int maxConcurrentTransfers;
}
