import 'package:drift/drift.dart' show Value;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/database/app_database.dart';
import '../../../core/providers.dart';

class NasConfigEditPage extends ConsumerStatefulWidget {
  const NasConfigEditPage({super.key, required this.configId});
  final String? configId;

  @override
  ConsumerState<NasConfigEditPage> createState() => _NasConfigEditPageState();
}

class _NasConfigEditPageState extends ConsumerState<NasConfigEditPage> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _baseUrl = TextEditingController();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _defaultPath = TextEditingController(text: '/');
  String _type = 'webdav';
  bool _trustSelfSigned = false;
  bool _loading = true;
  bool _testing = false;
  String? _testResult;

  @override
  void initState() {
    super.initState();
    if (widget.configId == null) {
      _loading = false;
    } else {
      _loadExisting();
    }
  }

  Future<void> _loadExisting() async {
    final db = ref.read(appDatabaseProvider);
    final c = await db.nasConfigDao.getById(widget.configId!);
    if (c != null && mounted) {
      _name.text = c.name;
      _baseUrl.text = c.baseUrl;
      _username.text = c.username;
      _password.text = c.passwordEncrypted;
      _defaultPath.text = c.defaultRemotePath;
      _type = c.type;
      _trustSelfSigned = c.trustSelfSigned;
    }
    if (mounted) setState(() => _loading = false);
  }

  @override
  void dispose() {
    _name.dispose();
    _baseUrl.dispose();
    _username.dispose();
    _password.dispose();
    _defaultPath.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.configId == null ? '新建连接' : '编辑连接'),
        actions: [
          IconButton(
            icon: const Icon(Icons.check),
            onPressed: _save,
            tooltip: '保存',
          ),
        ],
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _name,
              decoration: const InputDecoration(labelText: '名称', border: OutlineInputBorder()),
              validator: (v) => (v == null || v.isEmpty) ? '必填' : null,
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: _type,
              decoration: const InputDecoration(labelText: '协议', border: OutlineInputBorder()),
              items: const [
                DropdownMenuItem(value: 'webdav', child: Text('WebDAV')),
                DropdownMenuItem(value: 'sftp', child: Text('SFTP (未实现)'), enabled: false),
                DropdownMenuItem(value: 'smb', child: Text('SMB (未实现)'), enabled: false),
              ],
              onChanged: (v) => setState(() => _type = v ?? 'webdav'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _baseUrl,
              decoration: const InputDecoration(
                labelText: '地址',
                hintText: 'https://your-nas:5006',
                border: OutlineInputBorder(),
              ),
              validator: (v) => (v == null || v.isEmpty) ? '必填' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _username,
              decoration: const InputDecoration(labelText: '用户名', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _password,
              decoration: const InputDecoration(labelText: '密码', border: OutlineInputBorder()),
              obscureText: true,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _defaultPath,
              decoration: const InputDecoration(labelText: '默认路径', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            SwitchListTile(
              title: const Text('信任自签证书'),
              subtitle: const Text('仅对该连接的 HTTPS 自签证书放行'),
              value: _trustSelfSigned,
              onChanged: (v) => setState(() => _trustSelfSigned = v),
            ),
            const SizedBox(height: 24),
            FilledButton.tonalIcon(
              onPressed: _testing ? null : _testConnection,
              icon: _testing
                  ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.wifi_protected_setup),
              label: const Text('测试连接'),
            ),
            if (_testResult != null) ...[
              const SizedBox(height: 8),
              Text(_testResult!,
                  style: TextStyle(
                    color: _testResult!.startsWith('成功')
                        ? Colors.green
                        : Theme.of(context).colorScheme.error,
                  )),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _testConnection() async {
    setState(() {
      _testing = true;
      _testResult = null;
    });
    try {
      final factory = ref.read(adapterFactoryProvider);
      final entity = NasConfigEntity(
        id: widget.configId ?? '__test__',
        name: _name.text,
        type: _type,
        baseUrl: _baseUrl.text.trim(),
        username: _username.text,
        passwordEncrypted: _password.text,
        trustSelfSigned: _trustSelfSigned,
        defaultRemotePath: _defaultPath.text,
        extraJson: '{}',
        createdAt: DateTime.now(),
      );
      final adapter = factory.create(entity);
      final entries = await adapter.list(_defaultPath.text);
      await adapter.dispose();
      if (mounted) setState(() => _testResult = '成功：根目录 ${entries.length} 个条目');
    } catch (e, st) {
      if (kDebugMode) {
        debugPrint('[test-connection] failed for baseUrl=${_baseUrl.text.trim()} '
            'user=${_username.text} path=${_defaultPath.text} trustSelfSigned=$_trustSelfSigned');
        debugPrint('[test-connection] exception: $e');
        debugPrint('[test-connection] stack:\n$st');
      }
      if (mounted) setState(() => _testResult = '失败：$e');
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final db = ref.read(appDatabaseProvider);

    final companion = NasConfigsCompanion(
      id: widget.configId == null ? const Value.absent() : Value(widget.configId!),
      name: Value(_name.text),
      type: Value(_type),
      baseUrl: Value(_baseUrl.text.trim()),
      username: Value(_username.text),
      passwordEncrypted: Value(_password.text),
      trustSelfSigned: Value(_trustSelfSigned),
      defaultRemotePath: Value(_defaultPath.text),
    );

    await db.nasConfigDao.upsert(companion);
    // 失效 dio 缓存以便下次用新凭据。
    if (widget.configId != null) {
      ref.read(dioFactoryProvider).invalidate(widget.configId!);
    }
    if (mounted) context.pop();
  }
}
