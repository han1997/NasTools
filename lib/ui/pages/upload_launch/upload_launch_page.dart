import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';
import '../../../modules/upload/domain/upload_options.dart';
import '../../../modules/upload/providers.dart';
import '../../../modules/upload/service/preset_codec.dart';
import '../../widgets/upload_form_fields.dart';

class UploadLaunchPage extends ConsumerStatefulWidget {
  const UploadLaunchPage({super.key, required this.configId, required this.remoteRoot});
  final String configId;
  final String remoteRoot;

  @override
  ConsumerState<UploadLaunchPage> createState() => _UploadLaunchPageState();
}

class _UploadLaunchPageState extends ConsumerState<UploadLaunchPage> {
  final _filterRegex = TextEditingController();
  late final TextEditingController _remoteRoot;

  String? _localUri;
  String? _localLabel;
  int _chunkSizeMb = 8;
  OverwriteMode _overwriteMode = OverwriteMode.resumeOrOverwrite;
  bool _wifiOnly = false;
  bool _deleteAfterUpload = false;
  bool _starting = false;

  List<UploadPresetEntity> _presets = const [];

  @override
  void initState() {
    super.initState();
    _remoteRoot = TextEditingController(text: widget.remoteRoot);
    final settings = ref.read(settingsRepositoryProvider);
    _chunkSizeMb = settings.chunkSizeMb;
    _wifiOnly = settings.wifiOnly;
    unawaited(_loadPresets());
  }

  @override
  void dispose() {
    _filterRegex.dispose();
    _remoteRoot.dispose();
    super.dispose();
  }

  Future<void> _loadPresets() async {
    final list = await ref.read(uploadPresetDaoProvider).getAll();
    if (mounted) setState(() => _presets = list);
  }

  Future<void> _pickFolder() async {
    final path = await FilePicker.platform.getDirectoryPath();
    if (path == null) return;
    setState(() {
      _localUri = path;
      _localLabel = path;
    });
  }

  Future<void> _pickRemoteFolder() async {
    final current = _remoteRoot.text.trim().isEmpty ? '/' : _remoteRoot.text.trim();
    final selected = await context.push<String>(
      Uri(path: '/folder_picker/${widget.configId}', queryParameters: {
        'path': current,
        'title': '选择远端目录',
      }).toString(),
    );
    if (selected != null && mounted) {
      setState(() => _remoteRoot.text = selected);
    }
  }

  UploadOptions _currentOptions() => UploadOptions(
        chunkSizeBytes: _chunkSizeMb * 1024 * 1024,
        overwriteMode: _overwriteMode,
        wifiOnly: _wifiOnly,
        filterRegex:
            _filterRegex.text.trim().isEmpty ? null : _filterRegex.text.trim(),
        deleteAfterUpload: _deleteAfterUpload,
      );

  /// 载入预设：覆盖所有可编辑字段（远端目录、本地源、选项）。
  /// configId 由 URL 注入，受当前页面上下文绑定 —— 仅匹配 nasConfigId 的预设
  /// 才会出现在下拉里，因此不存在跨连接载入的情况。
  void _applyPreset(UploadPresetEntity p) {
    final options = PresetCodec.decode(p.optionsJson) ?? const UploadOptions();
    setState(() {
      _localUri = p.localUri;
      _localLabel = p.localLabel.isEmpty ? p.localUri : p.localLabel;
      _remoteRoot.text = p.remoteRoot;
      _chunkSizeMb = (options.chunkSizeBytes / (1024 * 1024)).round();
      _overwriteMode = options.overwriteMode;
      _wifiOnly = options.wifiOnly;
      _deleteAfterUpload = options.deleteAfterUpload;
      _filterRegex.text = options.filterRegex ?? '';
    });
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('已载入预设：${p.name}')),
    );
  }

  Future<void> _saveAsPreset() async {
    if (_localUri == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请先选择本地文件夹再保存预设')),
      );
      return;
    }
    final defaultName =
        '预设 ${DateFormat('yyyy-MM-dd HH:mm').format(DateTime.now())}';
    final name = await _promptText(
      title: '另存为预设',
      label: '预设名称',
      initial: defaultName,
    );
    if (name == null || name.trim().isEmpty) return;

    final companion = PresetCodec.buildCompanion(
      id: null,
      name: name,
      nasConfigId: widget.configId,
      localUri: _localUri!,
      localLabel: _localLabel ?? '',
      remoteRoot: _remoteRoot.text,
      options: _currentOptions(),
    );
    final id = await ref.read(uploadPresetDaoProvider).upsert(companion);
    await _loadPresets();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('已保存预设：$name'),
        action: SnackBarAction(
          label: '查看',
          onPressed: () => context.push('/presets/edit/$id'),
        ),
      ),
    );
  }

  Future<String?> _promptText({
    required String title,
    required String label,
    String initial = '',
  }) {
    final ctrl = TextEditingController(text: initial);
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: InputDecoration(labelText: label, border: const OutlineInputBorder()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
              child: const Text('确定')),
        ],
      ),
    );
  }

  Future<void> _start() async {
    if (_localUri == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请先选择本地文件夹')),
      );
      return;
    }
    setState(() => _starting = true);
    try {
      final service = ref.read(uploadServiceProvider);
      final id = await service.createFolderUploadTask(
        nasConfigId: widget.configId,
        localRootUri: _localUri!,
        remoteRoot:
            _remoteRoot.text.trim().isEmpty ? '/' : _remoteRoot.text.trim(),
        options: _currentOptions(),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('已创建任务 $id')),
      );
      context.go('/tasks');
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('失败：$e')));
      }
    } finally {
      if (mounted) setState(() => _starting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final matchingPresets =
        _presets.where((p) => p.nasConfigId == widget.configId).toList();
    final otherPresets =
        _presets.where((p) => p.nasConfigId != widget.configId).toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('发起上传'),
        actions: [
          PopupMenuButton<UploadPresetEntity>(
            icon: const Icon(Icons.bookmark_outline),
            tooltip: '载入预设',
            enabled: _presets.isNotEmpty,
            onSelected: _applyPreset,
            itemBuilder: (_) => [
              if (matchingPresets.isEmpty && otherPresets.isEmpty)
                const PopupMenuItem(enabled: false, child: Text('暂无预设')),
              for (final p in matchingPresets)
                PopupMenuItem(value: p, child: Text(p.name)),
              if (matchingPresets.isNotEmpty && otherPresets.isNotEmpty)
                const PopupMenuDivider(),
              for (final p in otherPresets)
                PopupMenuItem(
                  enabled: false,
                  child: Text('${p.name}（属于其它连接）',
                      style: Theme.of(context).textTheme.bodySmall),
                ),
            ],
          ),
          IconButton(
            icon: const Icon(Icons.bookmark_add_outlined),
            tooltip: '另存为预设',
            onPressed: _saveAsPreset,
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('目标', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _remoteRoot,
                    decoration: InputDecoration(
                      labelText: '远端目录',
                      hintText: '/backup/photos',
                      border: const OutlineInputBorder(),
                      suffixIcon: IconButton(
                        icon: const Icon(Icons.folder_open),
                        tooltip: '从远端选择',
                        onPressed: _pickRemoteFolder,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              leading: const Icon(Icons.folder_open),
              title: Text(_localLabel ?? '未选择本地文件夹'),
              subtitle: const Text('点击选择'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _pickFolder,
            ),
          ),
          const SizedBox(height: 12),
          UploadFormFields(
            chunkSizeMb: _chunkSizeMb,
            overwriteMode: _overwriteMode,
            wifiOnly: _wifiOnly,
            deleteAfterUpload: _deleteAfterUpload,
            filterRegexController: _filterRegex,
            onChunkSizeChanged: (v) => setState(() => _chunkSizeMb = v),
            onOverwriteModeChanged: (v) => setState(() => _overwriteMode = v),
            onWifiOnlyChanged: (v) => setState(() => _wifiOnly = v),
            onDeleteAfterUploadChanged: (v) =>
                setState(() => _deleteAfterUpload = v),
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _starting ? null : _start,
            icon: _starting
                ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.cloud_upload),
            label: const Text('开始上传'),
          ),
        ],
      ),
    );
  }
}
