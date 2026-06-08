import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

import '../app_database.dart';
import '../tables/upload_presets.dart';

part 'upload_preset_dao.g.dart';

@DriftAccessor(tables: [UploadPresets])
class UploadPresetDao extends DatabaseAccessor<AppDatabase>
    with _$UploadPresetDaoMixin {
  UploadPresetDao(super.db);

  Future<List<UploadPresetEntity>> getAll() =>
      (select(uploadPresets)..orderBy([(t) => OrderingTerm.desc(t.updatedAt)]))
          .get();

  Stream<List<UploadPresetEntity>> watchAll() =>
      (select(uploadPresets)..orderBy([(t) => OrderingTerm.desc(t.updatedAt)]))
          .watch();

  Future<UploadPresetEntity?> getById(String id) =>
      (select(uploadPresets)..where((t) => t.id.equals(id))).getSingleOrNull();

  Future<String> upsert(UploadPresetsCompanion entry) async {
    final hasId = entry.id.present && entry.id.value.isNotEmpty;
    final id = hasId ? entry.id.value : const Uuid().v4();
    final now = DateTime.now();
    final companion = entry.copyWith(
      id: Value(id),
      updatedAt: Value(now),
    );
    await into(uploadPresets).insertOnConflictUpdate(companion);
    return id;
  }

  Future<int> deleteById(String id) =>
      (delete(uploadPresets)..where((t) => t.id.equals(id))).go();

  Future<void> touchLastRun(String id) async {
    await (update(uploadPresets)..where((t) => t.id.equals(id)))
        .write(UploadPresetsCompanion(lastRunAt: Value(DateTime.now())));
  }
}
