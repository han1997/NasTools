import 'package:drift/drift.dart';

/// NAS 连接配置表。
///
/// 多 NAS 支持：每条记录对应一台 NAS / 一组凭据。
/// password 以简单的本地编码存储（非真正加密 —— Android Keystore 集成留待后续）。
@DataClassName('NasConfigEntity')
class NasConfigs extends Table {
  TextColumn get id => text()();
  TextColumn get name => text().withLength(min: 1, max: 64)();
  TextColumn get type => text()(); // webdav | sftp | smb
  TextColumn get baseUrl => text()();
  TextColumn get username => text().withDefault(const Constant(''))();
  TextColumn get passwordEncrypted => text().withDefault(const Constant(''))();
  BoolColumn get trustSelfSigned => boolean().withDefault(const Constant(false))();
  TextColumn get defaultRemotePath => text().withDefault(const Constant('/'))();
  TextColumn get extraJson => text().withDefault(const Constant('{}'))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column<Object>> get primaryKey => {id};
}
