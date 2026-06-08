import 'package:drift/drift.dart';

/// 文件去重 / 索引表 —— Phase 4 预留。
@DataClassName('FileIndexEntity')
class FileIndex extends Table {
  TextColumn get id => text()();
  TextColumn get sha256 => text()();
  IntColumn get size => integer()();
  DateTimeColumn get mtime => dateTime()();
  TextColumn get localUri => text()();
  TextColumn get remotePath => text().nullable()();
  TextColumn get nasConfigId => text().nullable()();
  DateTimeColumn get indexedAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column<Object>> get primaryKey => {id};
}
