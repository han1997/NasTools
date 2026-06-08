import 'package:flutter/material.dart';

import '../../modules/upload/domain/upload_options.dart';

/// 上传选项共用表单段。
///
/// 由发起上传页（`UploadLaunchPage`）与预设编辑页（`PresetEditPage`）共用，
/// 保证两处字段集合永远对齐。父 widget 负责持有所有状态（`StatefulWidget` 或
/// Riverpod），通过回调驱动；本 widget 不持有 Riverpod 依赖，便于复用到
/// 对话框 / 弹窗。
///
/// 受控字段：
/// - 分块大小（1..64 MB Slider）
/// - 冲突策略（Dropdown，含 `OverwriteMode.rename`）
/// - 仅 Wi-Fi
/// - 上传成功后删本地
/// - 文件名正则（TextField，由父持有 controller 以便重置/读取）
class UploadFormFields extends StatelessWidget {
  const UploadFormFields({
    super.key,
    required this.chunkSizeMb,
    required this.overwriteMode,
    required this.wifiOnly,
    required this.deleteAfterUpload,
    required this.filterRegexController,
    required this.onChunkSizeChanged,
    required this.onOverwriteModeChanged,
    required this.onWifiOnlyChanged,
    required this.onDeleteAfterUploadChanged,
  });

  final int chunkSizeMb;
  final OverwriteMode overwriteMode;
  final bool wifiOnly;
  final bool deleteAfterUpload;
  final TextEditingController filterRegexController;

  final ValueChanged<int> onChunkSizeChanged;
  final ValueChanged<OverwriteMode> onOverwriteModeChanged;
  final ValueChanged<bool> onWifiOnlyChanged;
  final ValueChanged<bool> onDeleteAfterUploadChanged;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('上传选项', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            Row(
              children: [
                const Text('分块大小'),
                const SizedBox(width: 16),
                Expanded(
                  child: Slider(
                    min: 1,
                    max: 64,
                    divisions: 63,
                    value: chunkSizeMb.toDouble().clamp(1, 64),
                    label: '${chunkSizeMb}MB',
                    onChanged: (v) => onChunkSizeChanged(v.round()),
                  ),
                ),
                Text('${chunkSizeMb}MB'),
              ],
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<OverwriteMode>(
              value: overwriteMode,
              decoration: const InputDecoration(
                labelText: '冲突处理',
                border: OutlineInputBorder(),
              ),
              items: const [
                DropdownMenuItem(
                  value: OverwriteMode.resumeOrOverwrite,
                  child: Text('续传或覆盖（推荐）'),
                ),
                DropdownMenuItem(
                  value: OverwriteMode.overwrite,
                  child: Text('总是覆盖'),
                ),
                DropdownMenuItem(
                  value: OverwriteMode.skip,
                  child: Text('远端已存在则跳过'),
                ),
                DropdownMenuItem(
                  value: OverwriteMode.rename,
                  child: Text('远端已存在则重命名 (foo_1.ext)'),
                ),
              ],
              onChanged: (v) {
                if (v != null) onOverwriteModeChanged(v);
              },
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: filterRegexController,
              decoration: const InputDecoration(
                labelText: '文件名正则（可选）',
                hintText: r'例：\.(jpg|jpeg|png)$  空=不过滤',
                helperText: '只匹配文件 basename；目录始终递归进入',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('仅在 Wi-Fi 下传输'),
              value: wifiOnly,
              onChanged: onWifiOnlyChanged,
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('上传成功后删本地文件'),
              subtitle: const Text('skip 不删；rename/续传完成都算成功'),
              value: deleteAfterUpload,
              onChanged: onDeleteAfterUploadChanged,
            ),
          ],
        ),
      ),
    );
  }
}
