# 技术设计:WebDAV 预设备份与恢复 + 版本号同步

对应 PRD 的 R1–R6、D1–D7。本文只描述技术设计,需求与验收在 `prd.md`。

## 1. 边界与分层

```
presentation/backup/BackupScreen + BackupViewModel     ← 新页面(设置 → 数据 → 备份与恢复)
        ↓
domain/backup/PresetBackupCodec                        ← 纯 JVM:序列化 / 解析 / 版本校验(可单测)
domain/backup/BackupMergePolicy                        ← 纯 JVM:同名幂等合并决策(可单测)
        ↓
data/backup/BackupRepository                           ← 编排:读库 → 编码 → 传输 → 写库
        ↓
StorageAdapterFactory.create(config)  →  StorageAdapter(upload/download/mkdir/stat)
UploadPresetRepository / NasConfigRepository           ← 既有写入能力
```

原则:编码与合并策略是**纯逻辑**,不依赖 Android/Compose,放下单测;传输与编排放
`data/backup`;页面只负责状态与交互。

## 2. 备份文件格式(v1)

```json
{
  "formatVersion": 1,
  "generatedAt": 1760000000000,
  "app": { "versionName": "0.2.6" },
  "configs": [
    { "name": "家庭 NAS", "type": "webdav", "baseUrl": "...", "username": "...",
      "password": "...", "trustSelfSigned": false, "defaultRemotePath": "/照片",
      "extraJson": null }
  ],
  "presets": [
    { "configName": "家庭 NAS", "name": "手机照片备份", "localUri": "...",
      "localLabel": "...", "remoteRoot": "/照片/相机胶卷",
      "optionsJson": "{...}" }
  ]
}
```

要点:
- **不写本地 id**(`id` / `nasConfigId` 跨设备无意义),连接用 `name` 反向引用,
  预设用 `configName` 指向连接。导入时在本机重新生成 UUID。
- `formatVersion` 为唯一兼容性开关。`> SUPPORTED` → 拒绝导入并报「备份由更新版本创建」。
- 不含 `createdAt/updatedAt/lastRunAt` 等本机时间语义字段。
- 不含除上述字段外的任何数据(R5)。

## 3. 远端位置与命名(D6)

- 目录:`normalize(selectedConfig.defaultRemotePath) + "/NasTools"`。
- 文件:上述目录下固定名 `backup.json`。
- 备份:先 `mkdir(目录)`(已存在则忽略,`WebDavAdapter.mkdir` 逐级容错),再
  `upload(路径, jsonBytes)`。覆盖语义由 PUT 自然满足。
- 恢复:先 `stat(路径)` 判断存在;不存在给出「未找到备份文件」而非静默失败。
- 路径归一化:`defaultRemotePath` 可能为 null / 空 / 无前导斜杠 / 末尾多斜杠,
  统一为 `/a/b` 形式(与 `BrowserScreen` 的路径风格一致)。

## 4. 恢复合并策略(D7)

输入:解析后的 `configs[]`、`presets[]`;本机现有连接与预设。

```
for each backupConfig (按顺序):
    if 名称在本机或本次已建连接中存在 → 复用该本地 id,跳过创建(记 skipped: 连接已存在)
    else → 新建 NasConfigEntity(id = UUID, createdAt = now),记 added: 连接 +1

for each backupPreset:
    configId = 名称 → 本地 id 的反查
    if configId == null → skipped: 找不到连接「X」
    else if 同一 configId 下同名预设已存在(含本次已导入)→ skipped: 预设已存在
    else → 新建 UploadPresetEntity(id = UUID, nasConfigId = configId,
           createdAt/updatedAt = now),记 added: 预设 +1
```

- 幂等:第二次执行时全部落入 skipped,结果一致。
- 复用连接**不覆盖**本机字段(D7);这是「不冲掉本机正确配置」的关键。
- 结果模型:`BackupResult(addedConnections, addedPresets, skipped: List<SkippedItem>)`,
  `SkippedItem(reason, label)` 供 UI 逐条展示。

## 5. 版本号同步(R3)

- `app/build.gradle.kts`:`buildFeatures { compose = true; buildConfig = true }`。
- 设置页「关于」改为读 `BuildConfig.VERSION_NAME`(debug 自动带 `-debug` 后缀,
  因 `versionNameSuffix = "-debug"` 作用于 `BuildConfig` 字段)。
- 原型「关于此设计」写的是原型基准版本,不属应用版本,不必对齐;本次只改应用代码。

## 6. 页面与状态(Compose)

`BackupScreen` 状态:

| 状态 | 触发 | 表现 |
|---|---|---|
| 空闲 | 进入 | 两个动作:备份到 WebDAV / 从 WebDAV 恢复 |
| 选择连接 | 点动作 | 连接列表(复用 `ConnectionSelectScreen` 样式),无连接时引导新建 |
| 备份确认 | 选定连接后 | `NasConfirmDialog`:写明目标路径 + 「备份包含明文连接密码」风险;确认才执行 |
| 进行中 | 执行中 | 按钮禁用 + 进度/加载;防重复点击 |
| 成功(备份) | 完成 | Snackbar/结果区:成功 + 远端路径 |
| 成功(恢复) | 完成 | 结果区:新增 N 连接 / M 预设 + 跳过逐条原因 |
| 失败 | 任一步异常 | 可理解错误文案(网络/目录/权限/未找到/版本不支持),不静默 |

原型(R4)必须先补齐同一组场景并经用户确认。原型场景集:
设置页「数据」分组;备份与恢复页;选择连接;备份风险确认;恢复结果(含跳过条目);
失败态。

## 7. 兼容性与回滚

- 备份格式新增 `formatVersion = 1`,未来加字段时递增;旧版本解析新文件直接拒绝,
  不做「尽力而为」的部分导入(R6)。
- 无数据库 schema 变更 → 无 migration、无回滚风险。
- 回滚方式:功能整体位于新页面 + 新包,移除入口即停用;不影响既有上传链路。

## 8. 安全说明(已由用户确认)

备份文件含**明文连接密码**。这是用户的明确取舍(D1),但设计上:
备份前强制风险确认;备份内容严格限定为连接 + 预设;文案不得暗示「已加密」。
「备份加密 / 密码加密」属 `10-01-credential-encryption` 任务。

## 9. 测试设计

纯 JVM(`app/src/test`):
1. `PresetBackupCodecTest`:编码 → 解析往返一致;未知 `formatVersion` 被拒绝;
   字段缺失/空值不崩。
2. `BackupMergePolicyTest`:空库全新增;同名连接复用不覆盖;同连接同名预设跳过;
   连接缺失的预设跳过并给出原因;**同一输入跑两次结果一致**(幂等)。
3. `BackupRemotePathTest`:路径归一化 null / 空 / 无前导斜杠 / 重复斜杠。

设备相关(无 adb,交由用户真机验收):备份上传、恢复下载、失败提示、版本号显示。