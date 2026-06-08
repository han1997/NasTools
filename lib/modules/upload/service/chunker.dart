import 'dart:async';

/// 把任意流按 [chunkSize] 切片重打包。
///
/// 用于把 [LocalSource.readChunked] 返回的流再细化成上传层期望的 chunk 大小。
class StreamChunker {
  StreamChunker._();

  static Stream<List<int>> rechunk(Stream<List<int>> input, int chunkSize) async* {
    final buffer = <int>[];
    await for (final part in input) {
      buffer.addAll(part);
      while (buffer.length >= chunkSize) {
        final chunk = buffer.sublist(0, chunkSize);
        buffer.removeRange(0, chunkSize);
        yield chunk;
      }
    }
    if (buffer.isNotEmpty) yield buffer;
  }
}
