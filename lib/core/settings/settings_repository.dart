import 'package:shared_preferences/shared_preferences.dart';

/// 简单的 KV 设置仓库。
///
/// 仅供 UI 偏好（主题、并发数、默认 chunk size、仅 Wi-Fi 等）使用。
/// 复杂结构走 Drift。
class SettingsRepository {
  SettingsRepository(this._prefs);
  final SharedPreferences _prefs;

  static const _kChunkSizeMb = 'upload.chunkSizeMb';
  static const _kMaxConcurrent = 'task.maxConcurrent';
  static const _kWifiOnly = 'upload.wifiOnly';
  static const _kThemeMode = 'ui.themeMode';
  static const _kBrowserViewMode = 'browser.viewMode';

  int get chunkSizeMb => _prefs.getInt(_kChunkSizeMb) ?? 8;
  Future<void> setChunkSizeMb(int v) => _prefs.setInt(_kChunkSizeMb, v);

  int get maxConcurrent => _prefs.getInt(_kMaxConcurrent) ?? 3;
  Future<void> setMaxConcurrent(int v) => _prefs.setInt(_kMaxConcurrent, v);

  bool get wifiOnly => _prefs.getBool(_kWifiOnly) ?? false;
  Future<void> setWifiOnly(bool v) => _prefs.setBool(_kWifiOnly, v);

  /// 'system' | 'light' | 'dark'
  String get themeMode => _prefs.getString(_kThemeMode) ?? 'system';
  Future<void> setThemeMode(String v) => _prefs.setString(_kThemeMode, v);

  /// 'list' | 'grid'
  String get browserViewMode =>
      _prefs.getString(_kBrowserViewMode) ?? 'list';
  Future<void> setBrowserViewMode(String v) =>
      _prefs.setString(_kBrowserViewMode, v);
}
