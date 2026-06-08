import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';
import '../../../modules/upload/domain/upload_options.dart';
import '../../../modules/upload/service/preset_codec.dart';
import '../../widgets/upload_form_fields.dart';

class PresetEditPage extends ConsumerStatefulWidget {
  const PresetEditPage({super.key, required this.presetId});
  final String? presetId;

  @override
  ConsumerState<PresetEditPage> createState() => _PresetEditPageState();
}

class _PresetEditPageState extends ConsumerState<PresetEditPage> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _remoteRoot = TextEditingController(text: '/');
  final _filterRegex = TextEditingController();

  String? _nasConfigId;
  String? _localUri;
  String _localLabel = '';
  int _chunkSizeMb = 8;
  OverwriteMode _overwriteMode = OverwriteMode.resumeOrOverwrite;
  bool _wifiOnly = false;
  bool _deleteAfterUpload = false;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    if (widget.presetId == null) {
      _loading = false;
    } else {
      _loadExisting();
    }
  }

  Future<void> _loadExisting() async {
    final p = await ref.read(uploadPresetDaoProvider).getById(widget.presetId!);
    if (p != null && mounted) {
      _name.text = p.name;
      _remoteRoot.text = p.remoteRoot;
      _nasConfigId = p.nasConfigId;
      _localUri = p.localUri;
      _localLabel = p.localLabel;
      final options = PresetCodec.decode(p.optionsJson);
      if (options != null) {
        _chunkSizeMb = (options.chunkSizeBytes / (1024 * 1024)).round();
        _overwriteMode = options.overwriteMode;
        _wifiOnly = options.wifiOnly;
        _filterRegex.text = options.filterRegex ?? '';
        _deleteAfterUpload = options.deleteAfterUpload;
      }
    }
    if (mounted) setState(() => _loading = false);
  }

  @override
  void dispose() {
    _name.dispose();
    _remoteRoot.dispose();
    _filterRegex.dispose();
    super.dispose();
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
    final configId = _nasConfigId;
    if (configId == null) return;
    final current = _remoteRoot.text.trim().isEmpty ? '/' : _remoteRoot.text.trim();
    final selected = await context.push<String>(
      Uri(path: '/folder_picker/$configId', queryParameters: {
        'path': current,
        'title': '选择远端目录',
      }).toString(),
    );
    if (selected != null && mounted) {
      setState(() => _remoteRoot.text = selected);
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    if (_nasConfigId == null) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请选择连接')));
      return;
    }
    if (_localUri == null) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请选择本地文件夹')));
      return;
    }
    final options = UploadOptions(
      chunkSizeBytes: _chunkSizeMb * 1024 * 1024,
      overwriteMode: _overwriteMode,
      wifiOnly: _wifiOnly,
      filterRegex:
          _filterRegex.text.trim().isEmpty ? null : _filterRegex.text.trim(),
      deleteAfterUpload: _deleteAfterUpload,
    );
    final companion = PresetCodec.buildCompanion(
      id: widget.presetId,
      name: _name.text,
      nasConfigId: _nasConfigId!,
      localUri: _localUri!,
      localLabel: _localLabel,
      remoteRoot: _remoteRoot.text,
      options: options,
    );
    await ref.read(uploadPresetDaoProvider).upsert(companion);
    if (mounted) context.pop();
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final db = ref.watch(appDatabaseProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.presetId == null ? '新建预设' : '编辑预设'),
        actions: [
          IconButton(
              icon: const Icon(Icons.check), onPressed: _save, tooltip: '保存'),
        ],
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _name,
              decoration: const InputDecoration(
                  labelText: '预设名称', border: OutlineInputBorder()),
              validator: (v) => (v == null || v.trim().isEmpty) ? '必填' : null,
            ),
            const SizedBox(height: 12),
            FutureBuilder<List<NasConfigEntity>>(
              future: db.nasConfigDao.getAll(),
              builder: (context, snap) {
                final list = snap.data ?? const [];
                return DropdownButtonFormField<String>(
                  value: _nasConfigId,
                  decoration: const InputDecoration(
                      labelText: '连接', border: OutlineInputBorder()),
                  items: list
                      .map((c) => DropdownMenuItem(
                            value: c.id,
                            child: Text('${c.name} (${c.type.toUpperCase()})'),
                          ))
                      .toList(),
                  onChanged: (v) => setState(() => _nasConfigId = v),
                );
              },
            ),
            const SizedBox(height: 12),
            Card(
              child: ListTile(
                leading: const Icon(Icons.folder_open),
                title: Text(_localLabel.isEmpty
                    ? (_localUri ?? '未选择本地文件夹')
                    : _localLabel),
                subtitle: const Text('点击选择'),
                trailing: const Icon(Icons.chevron_right),
                onTap: _pickFolder,
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _remoteRoot,
              decoration: InputDecoration(
                labelText: '远端目录',
                hintText: '/backup/photos',
                border: const OutlineInputBorder(),
                suffixIcon: IconButton(
                  icon: const Icon(Icons.folder_open),
                  tooltip: '从远端选择',
                  onPressed: _nasConfigId == null ? null : _pickRemoteFolder,
                ),
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
              onOverwriteModeChanged: (v) =>
                  setState(() => _overwriteMode = v),
              onWifiOnlyChanged: (v) => setState(() => _wifiOnly = v),
              onDeleteAfterUploadChanged: (v) =>
                  setState(() => _deleteAfterUpload = v),
            ),
          ],
        ),
      ),
    );
  }
}
