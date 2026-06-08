import '../adapter_capabilities.dart';
import '../storage_adapter.dart';

/// Local 适配器占位 —— 用于把 SAF 路径（Uri）作为"远端"提供读访问，
/// 未来可用于本地→本地同步。当前未实现。
class LocalAdapter implements StorageAdapter {
  @override
  AdapterCapabilities get capabilities => const AdapterCapabilities();

  @override
  Future<void> ping() => throw UnimplementedError();

  @override
  Future<List<RemoteEntry>> list(String path) => throw UnimplementedError();

  @override
  Future<RemoteStat?> stat(String path) => throw UnimplementedError();

  @override
  Stream<List<int>> readStream(String path, {int? start, int? end}) =>
      throw UnimplementedError();

  @override
  Future<void> writeStream(
    String path,
    Stream<List<int>> data, {
    int? totalLength,
    int offset = 0,
    void Function(int sent)? onProgress,
    TransferCancelToken? cancel,
  }) => throw UnimplementedError();

  @override
  Future<void> mkdir(String path, {bool recursive = true}) => throw UnimplementedError();

  @override
  Future<void> delete(String path) => throw UnimplementedError();

  @override
  Future<void> move(String from, String to) => throw UnimplementedError();

  @override
  Future<void> copy(String from, String to) => throw UnimplementedError();

  @override
  Future<void> dispose() async {}
}
