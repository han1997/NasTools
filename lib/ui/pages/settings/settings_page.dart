import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/background/service_bridge.dart';
import '../../../core/cache/preview_cache.dart';
import '../../../core/providers.dart';
import '../../../core/utils/bytes_utils.dart';

class SettingsPage extends ConsumerStatefulWidget {
  const SettingsPage({super.key});

  @override
  ConsumerState<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends ConsumerState<SettingsPage> {
  late int _chunkSizeMb;
  late int _maxConcurrent;
  late bool _wifiOnly;
  late String _themeMode;

  @override
  void initState() {
    super.initState();
    final s = ref.read(settingsRepositoryProvider);
    _chunkSizeMb = s.chunkSizeMb;
    _maxConcurrent = s.maxConcurrent;
    _wifiOnly = s.wifiOnly;
    _themeMode = s.themeMode;
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.read(settingsRepositoryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ListView(
        children: [
          const _SectionHeader('上传'),
          ListTile(
            title: const Text('默认分块大小'),
            subtitle: Slider(
              min: 1,
              max: 64,
              divisions: 63,
              value: _chunkSizeMb.toDouble(),
              label: '${_chunkSizeMb}MB',
              onChanged: (v) => setState(() => _chunkSizeMb = v.round()),
              onChangeEnd: (v) => s.setChunkSizeMb(v.round()),
            ),
            trailing: Text('${_chunkSizeMb}MB'),
          ),
          ListTile(
            title: const Text('并发任务数'),
            subtitle: Slider(
              min: 1,
              max: 8,
              divisions: 7,
              value: _maxConcurrent.toDouble(),
              label: '$_maxConcurrent',
              onChanged: (v) => setState(() => _maxConcurrent = v.round()),
              onChangeEnd: (v) => s.setMaxConcurrent(v.round()),
            ),
            trailing: Text('$_maxConcurrent'),
          ),
          SwitchListTile(
            title: const Text('仅在 Wi-Fi 下传输'),
            value: _wifiOnly,
            onChanged: (v) {
              setState(() => _wifiOnly = v);
              s.setWifiOnly(v);
            },
          ),
          const _SectionHeader('外观'),
          ListTile(
            title: const Text('主题'),
            trailing: DropdownButton<String>(
              value: _themeMode,
              items: const [
                DropdownMenuItem(value: 'system', child: Text('跟随系统')),
                DropdownMenuItem(value: 'light', child: Text('浅色')),
                DropdownMenuItem(value: 'dark', child: Text('深色')),
              ],
              onChanged: (v) {
                if (v == null) return;
                setState(() => _themeMode = v);
                s.setThemeMode(v);
              },
            ),
          ),
          const _SectionHeader('系统'),
          ListTile(
            leading: const Icon(Icons.battery_saver),
            title: const Text('电池优化白名单'),
            subtitle: const Text('防止系统冻结后台上传'),
            onTap: () async {
              try {
                await ServiceBridge.instance.requestIgnoreBatteryOptimizations();
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
                }
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.notifications),
            title: const Text('通知权限'),
            onTap: () async {
              try {
                final granted = await ServiceBridge.instance.requestNotificationPermission();
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(granted ? '已授予通知权限' : '已拒绝 —— 后台进度通知将不可见')),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
                }
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.cleaning_services_outlined),
            title: const Text('清理 7 天前日志'),
            onTap: () async {
              final db = ref.read(appDatabaseProvider);
              final n = await db.logDao.purgeOlderThan(const Duration(days: 7));
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('清理 $n 条')));
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.image_not_supported_outlined),
            title: const Text('清理预览缓存'),
            subtitle: const Text('删除已下载的图片/视频预览文件'),
            onTap: () async {
              final r = await PreviewCache.clearAll();
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('已清理 ${r.files} 个文件 (${BytesFormat.human(r.bytes)})')),
                );
              }
            },
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        text,
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.w600,
            ),
      ),
    );
  }
}
