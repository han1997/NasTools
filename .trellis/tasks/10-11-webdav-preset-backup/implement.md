# 实施计划:WebDAV 预设备份与恢复 + 版本号同步

对应 `prd.md`(R1–R6)与 `design.md`。执行顺序即下列清单顺序;原型确认是代码前置门槛。

## 阶段 0:原型先行(R4,强制门槛)

- [x] 0.1 在 `prototypes/nastools-ui/` 补场景(见 design.md §6):
      设置页「数据」分组 → 备份与恢复页(两个动作)、选择连接、备份风险确认、
      恢复结果(含跳过条目)、失败态。演示数据自包含,不连接真实 NAS。
- [x] 0.2 本地打开原型自检:明/暗、320/390dp、`node smoke-test.cjs`(如环境可用)。
- [x] 0.3 **提交原型改动,请用户确认。**确认前不得进入阶段 1。
- [x] 0.4 确认后在 `BASELINE.md` 记录版本、改动页面、用户确认(AC6)。

## 阶段 1:纯逻辑(可先于 UI 完成,全程单测驱动)

- [x] 1.1 `domain/backup/PresetBackupCodec.kt`:备份 DTO + 编码/解析 + `formatVersion` 校验。
- [x] 1.2 `domain/backup/BackupMergePolicy.kt`:幂等合并决策,输出 `BackupResult` / `SkippedItem`。
- [x] 1.3 `domain/backup/BackupRemotePath.kt`:远端目录/文件的归一化与拼接。
- [x] 1.4 单测:`PresetBackupCodecTest`、`BackupMergePolicyTest`、`BackupRemotePathTest`
      (design.md §9 的断言点,含幂等与版本拒绝)。
- [x] 1.5 `./gradlew :app:testDebugUnitTest` 通过。

## 阶段 2:数据编排

- [x] 2.1 `data/backup/BackupRepository.kt`:
      `backup(configId): Result<String>`(读库→编码→mkdir→upload→返回远端路径);
      `restore(configId): Result<BackupResult>`(stat→download→解析→合并→写库)。
      复用 `StorageAdapterFactory` 与两个既有 Repository,不新造数据访问。
- [x] 2.2 错误归一化:目录创建失败、上传/下载失败、未找到备份、版本不支持、解析失败
      各自映射为可读文案(AC8/AC9)。

## 阶段 3:UI(原型已确认后)

- [x] 3.1 `presentation/backup/BackupViewModel.kt`:状态机(design.md §6 表),防重复点击。
- [x] 3.2 `presentation/backup/BackupScreen.kt`:两动作 + 连接选择 + 风险确认 +
      结果汇总 + 失败态;复用 `NasScaffold` / `NasTopAppBar` / `NasConfirmDialog` /
      `NasStatusBadge` 与 theme token,不新增平行组件。
- [x] 3.3 设置页新增「数据」分组入口(`SettingsScreen.kt`),`NavGraph.kt` 加 `backup` 路由
      (子页面,不传 bottomBar)。
- [x] 3.4 浅色/深色 `@Preview`;`nasMotionEnabled` 沿用既有动画开关。

## 阶段 4:版本号(R3)

- [x] 4.1 `app/build.gradle.kts`:`buildFeatures { buildConfig = true }`。
- [x] 4.2 `SettingsScreen.kt`:版本号改读 `BuildConfig.VERSION_NAME`,删除硬编码 `0.1.0`。

## 阶段 5:验证与交付

- [x] 5.1 `./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest` 全绿,
      并 `ls` 核对 debug + release 两个 APK 都产出(0.2.5 曾漏 release,重点检查)。
- [x] 5.2 版本号追加第三级(versionCode +1)、CHANGELOG 追加版本段。
- [ ] 5.3 核对 AC1–AC9 逐条给出证据;AC3/AC4/AC5/AC8 真机项标为「待用户验收」。
- [ ] 5.4 提交(与 Trellis 工具链改动分离)、推送远程。

## 风险与回滚点

| 风险 | 处置 |
|---|---|
| 误把 `localUri` 当有效来源预检 → 与 D3 冲突 | 恢复流程不做本地来源校验;运行失败由既有链路报错(AC3) |
| 同名连接被备覆盖 | 合并策略只复用 id、不写字段;单测锁定(D7/AC2) |
| 备份内容越界写入其它数据 | Codec 只序列化白名单字段;单测断言输出键集合(AC4) |
| 覆盖式备份误删历史 | 本任务有意为单份备份(D6);历史版本为 Out of Scope |
| 大备份文件内存 | 预设/连接为小 JSON;`download()` 返回 `ByteArray` 足够;无需流式 |

## 依赖与顺序

- 阶段 1–2 不依赖阶段 3 的原型确认,可先行。
- 阶段 3 强依赖阶段 0.3 用户确认(R4)——**先原型、后代码**。
- 版本号(阶段 4)独立,可随时插入。