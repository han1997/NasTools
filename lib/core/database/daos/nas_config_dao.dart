import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

import '../app_database.dart';
import '../tables/nas_configs.dart';

part 'nas_config_dao.g.dart';

@DriftAccessor(tables: [NasConfigs])
class NasConfigDao extends DatabaseAccessor<AppDatabase> with _$NasConfigDaoMixin {
  NasConfigDao(super.db);

  Future<List<NasConfigEntity>> getAll() => select(nasConfigs).get();
  Stream<List<NasConfigEntity>> watchAll() => select(nasConfigs).watch();
  Future<NasConfigEntity?> getById(String id) =>
      (select(nasConfigs)..where((t) => t.id.equals(id))).getSingleOrNull();

  Future<String> upsert(NasConfigsCompanion entry) async {
    final hasId = entry.id.present && entry.id.value.isNotEmpty;
    final id = hasId ? entry.id.value : const Uuid().v4();
    final companion = entry.copyWith(id: Value(id));
    await into(nasConfigs).insertOnConflictUpdate(companion);
    return id;
  }

  Future<int> deleteById(String id) =>
      (delete(nasConfigs)..where((t) => t.id.equals(id))).go();
}
