import 'package:drift/drift.dart';

/// 上传预设模板 —— 把一次"文件夹上传"的参数（NAS / 本地源 / 远端目录 / 选项）
/// 保存为可复用模板。点击「运行」会基于此创建一个新的 `Tasks` 行交给
/// `UploadTaskExecutor` 跑，运行实例与模板分离。
///
/// `optionsJson` 存 `UploadOptions` 的 JSON 序列化结果（包含 chunkSize /
/// overwriteMode / wifiOnly / preserveStructure / filterRegex /
/// deleteAfterUpload）。扩字段不必改表。
@DataClassName('UploadPresetEntity')
class UploadPresets extends Table {
  TextColumn get id => text()();
  TextColumn get name => text().withLength(min: 1, max: 64)();
  TextColumn get nasConfigId => text()
      .customConstraint('NOT NULL REFERENCES nas_configs(id) ON DELETE CASCADE')();
  TextColumn get localUri => text()();
  TextColumn get localLabel => text().withDefault(const Constant(''))();
  TextColumn get remoteRoot => text().withDefault(const Constant('/'))();
  TextColumn get optionsJson => text().withDefault(const Constant('{}'))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get lastRunAt => dateTime().nullable()();

  @override
  Set<Column<Object>> get primaryKey => {id};
}
