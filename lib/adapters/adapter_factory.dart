import '../core/database/app_database.dart';
import '../core/network/dio_factory.dart';
import 'storage_adapter.dart';
import 'webdav/webdav_adapter.dart';

/// 根据 [NasConfigEntity.type] 创建对应的 [StorageAdapter]。
///
/// 未来增加 SFTP / SMB 协议时，只在此处加分支，业务层零改动。
class AdapterFactory {
  AdapterFactory(this._dioFactory);

  final DioFactory _dioFactory;

  StorageAdapter create(NasConfigEntity config) {
    switch (config.type) {
      case 'webdav':
        return WebDavAdapter(
          dio: _dioFactory.forNas(config),
          baseUrl: config.baseUrl,
        );
      case 'sftp':
        throw UnimplementedError('SFTP adapter 计划于 Phase 2 之后实现');
      case 'smb':
        throw UnimplementedError('SMB adapter 计划于 Phase 3 之后实现');
      default:
        throw ArgumentError('未知的 NAS 协议类型: ${config.type}');
    }
  }
}
