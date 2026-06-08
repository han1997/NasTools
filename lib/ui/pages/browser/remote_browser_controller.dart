import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../adapters/storage_adapter.dart';
import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';
import '../../../core/utils/path_utils.dart';

/// 视图模式（持久化在 SettingsRepository `browser.viewMode`）。
enum BrowserViewMode { list, grid }

/// 远端浏览状态。
class RemoteBrowserState {
  const RemoteBrowserState({
    required this.path,
    required this.entries,
    this.viewMode = BrowserViewMode.list,
    this.selectionMode = false,
    this.selected = const <String>{},
  });

  final String path;
  final AsyncValue<List<RemoteEntry>> entries;
  final BrowserViewMode viewMode;
  final bool selectionMode;
  final Set<String> selected;

  RemoteBrowserState copyWith({
    String? path,
    AsyncValue<List<RemoteEntry>>? entries,
    BrowserViewMode? viewMode,
    bool? selectionMode,
    Set<String>? selected,
  }) {
    return RemoteBrowserState(
      path: path ?? this.path,
      entries: entries ?? this.entries,
      viewMode: viewMode ?? this.viewMode,
      selectionMode: selectionMode ?? this.selectionMode,
      selected: selected ?? this.selected,
    );
  }
}

/// 远端浏览控制器。
///
/// 把"加载目录 / 切目录 / 新建子目录 / 单项 CRUD / 批量动作 / 视图切换 /
/// 多选"等通用动作收口，被 [`RemoteBrowserPage`] 和
/// [`RemoteFolderPickerPage`] 共用（后者只用到部分能力）。
///
/// 设计要点：
/// - 每个 (configId, initialPath) 对应一个独立 Notifier（family.autoDispose）
/// - 每次动作内部 `create + dispose` 一个 adapter，避免长期持有 HTTP client
///   状态；WebDavAdapter.dispose() 是 no-op，dio 由 DioFactory 统一管理
class RemoteBrowserController extends StateNotifier<RemoteBrowserState> {
  RemoteBrowserController(this._ref, this.configId, String initialPath)
      : super(RemoteBrowserState(
          path: RemotePath.normalize(initialPath),
          entries: const AsyncValue.loading(),
          viewMode: _readViewMode(_ref),
        )) {
    refresh();
  }

  final Ref _ref;
  final String configId;

  static BrowserViewMode _readViewMode(Ref ref) {
    final v = ref.read(settingsRepositoryProvider).browserViewMode;
    return v == 'grid' ? BrowserViewMode.grid : BrowserViewMode.list;
  }

  Future<NasConfigEntity> _config() async {
    final db = _ref.read(appDatabaseProvider);
    final c = await db.nasConfigDao.getById(configId);
    if (c == null) throw StateError('NAS 配置不存在');
    return c;
  }

  Future<T> _withAdapter<T>(Future<T> Function(StorageAdapter) action) async {
    final factory = _ref.read(adapterFactoryProvider);
    final adapter = factory.create(await _config());
    try {
      return await action(adapter);
    } finally {
      await adapter.dispose();
    }
  }

  /// 重新加载当前 path 下的条目，按目录在前 / 名称升序排序。
  Future<void> refresh() async {
    state = state.copyWith(entries: const AsyncValue.loading());
    try {
      final entries = await _withAdapter((a) => a.list(state.path));
      entries.sort((a, b) {
        if (a.isDirectory != b.isDirectory) return a.isDirectory ? -1 : 1;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });
      // 切换目录或刷新时清掉已选 —— 路径变了旧 path 集不再有效，
      // 但保留 selectionMode（用户可继续在新目录里挑）。
      final keep = state.selected
          .where((p) => entries.any((e) => e.path == p))
          .toSet();
      state = state.copyWith(
        entries: AsyncValue.data(entries),
        selected: keep,
      );
    } catch (e, st) {
      state = state.copyWith(entries: AsyncValue.error(e, st));
    }
  }

  Future<void> goInto(RemoteEntry entry) async {
    if (!entry.isDirectory) return;
    state = state.copyWith(
      path: RemotePath.normalize(entry.path),
      entries: const AsyncValue.loading(),
      selected: const <String>{},
    );
    await refresh();
  }

  Future<void> goTo(String path) async {
    state = state.copyWith(
      path: RemotePath.normalize(path),
      entries: const AsyncValue.loading(),
      selected: const <String>{},
    );
    await refresh();
  }

  Future<void> goParent() async {
    final parent = RemotePath.parent(state.path);
    if (parent == state.path) return;
    await goTo(parent);
  }

  Future<void> mkdir(String name) async {
    await _withAdapter((a) => a.mkdir(RemotePath.join(state.path, name)));
    await refresh();
  }

  // ---- View mode -----------------------------------------------------------

  Future<void> toggleViewMode() async {
    final next = state.viewMode == BrowserViewMode.list
        ? BrowserViewMode.grid
        : BrowserViewMode.list;
    state = state.copyWith(viewMode: next);
    await _ref
        .read(settingsRepositoryProvider)
        .setBrowserViewMode(next == BrowserViewMode.grid ? 'grid' : 'list');
  }

  // ---- Selection -----------------------------------------------------------

  void enterSelection(RemoteEntry seed) {
    state = state.copyWith(
      selectionMode: true,
      selected: {...state.selected, seed.path},
    );
  }

  void exitSelection() {
    state = state.copyWith(
      selectionMode: false,
      selected: const <String>{},
    );
  }

  void toggleSelected(RemoteEntry entry) {
    final next = {...state.selected};
    if (!next.add(entry.path)) next.remove(entry.path);
    state = state.copyWith(
      selected: next,
      // 全部取消时自动退出多选
      selectionMode: next.isEmpty ? false : state.selectionMode,
    );
  }

  // ---- 单项 CRUD ------------------------------------------------------------

  Future<void> rename(RemoteEntry entry, String newName) async {
    final parent = RemotePath.parent(entry.path);
    final to = RemotePath.join(parent, newName);
    if (to == entry.path) return;
    await _withAdapter((a) => a.move(entry.path, to));
    await refresh();
  }

  Future<void> moveTo(RemoteEntry entry, String targetDir) async {
    final to = RemotePath.join(targetDir, RemotePath.basename(entry.path));
    await _withAdapter((a) => a.move(entry.path, to));
    await refresh();
  }

  Future<void> copyTo(RemoteEntry entry, String targetDir) async {
    final to = RemotePath.join(targetDir, RemotePath.basename(entry.path));
    await _withAdapter((a) => a.copy(entry.path, to));
    await refresh();
  }

  Future<void> deleteOne(RemoteEntry entry) async {
    await _withAdapter((a) => a.delete(entry.path));
    await refresh();
  }

  // ---- 批量动作（顺序执行，WebDAV 对同目录并发 MOVE/DELETE 敏感） -------

  Future<({int ok, int failed})> batchDelete(
    List<RemoteEntry> entries, {
    void Function(int done, int total)? onProgress,
  }) async {
    var ok = 0;
    var failed = 0;
    await _withAdapter((a) async {
      for (var i = 0; i < entries.length; i++) {
        try {
          await a.delete(entries[i].path);
          ok++;
        } catch (_) {
          failed++;
        }
        onProgress?.call(i + 1, entries.length);
      }
    });
    await refresh();
    return (ok: ok, failed: failed);
  }

  Future<({int ok, int failed})> batchMoveTo(
    List<RemoteEntry> entries,
    String targetDir, {
    void Function(int done, int total)? onProgress,
  }) async {
    var ok = 0;
    var failed = 0;
    await _withAdapter((a) async {
      for (var i = 0; i < entries.length; i++) {
        final to = RemotePath.join(targetDir, RemotePath.basename(entries[i].path));
        try {
          await a.move(entries[i].path, to);
          ok++;
        } catch (_) {
          failed++;
        }
        onProgress?.call(i + 1, entries.length);
      }
    });
    await refresh();
    return (ok: ok, failed: failed);
  }
}

/// 每个 (configId, initialPath) 一个独立 Notifier；离开页面自动 dispose。
final remoteBrowserControllerProvider = StateNotifierProvider.autoDispose
    .family<RemoteBrowserController, RemoteBrowserState, ({String configId, String initialPath})>(
  (ref, key) => RemoteBrowserController(ref, key.configId, key.initialPath),
);
