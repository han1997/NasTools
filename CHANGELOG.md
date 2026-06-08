# Changelog

本项目所有重要变更都会记录在本文件中。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- **远端文件管理器升级** —— 把原"只能浏览 + 新建文件夹"的页面升级为完整 WebDAV 管理器：列表/网格视图切换并跨 session 保留（`SettingsRepository.browserViewMode`）、长按多选、单项重命名 / 移动 / 复制 / 删除、批量删除 / 移动（顺序执行，WebDAV 对同目录并发动作敏感）、下载到本地 (SAF "另存为")、上传单文件或文件夹。状态管理用 `RemoteBrowserController`（StateNotifier）收口，被浏览器页与目录选择器复用。`lib/ui/pages/browser/remote_browser_controller.dart`、`lib/ui/pages/browser/remote_browser_page.dart`、`lib/core/settings/settings_repository.dart`
- **远端目录选择器** —— 预设编辑页与发起上传页的"远端目录"字段不再是手动输入框：旁边新增"浏览"按钮，弹出 `RemoteFolderPickerPage`，可层层进入子目录，点"选择此目录"回填路径。复用 `RemoteBrowserController` 的导航能力。新路由 `/folder_picker/:configId`。`lib/ui/pages/browser/remote_folder_picker_page.dart`、`lib/ui/router/app_router.dart`、`lib/ui/pages/presets/edit_page.dart`、`lib/ui/pages/upload_launch/upload_launch_page.dart`
- **图片/视频在线预览** —— 点击远端文件下载到 `<cache>/preview/<configId>/<sha1(remotePath)>${ext}`（命中即跳过），图片走内置 `PhotoView` 全屏页 (`/preview/image` 路由)，其它走 Android `Intent.ACTION_VIEW` + FileProvider 调外部应用。下载过程显示进度条和取消按钮。`lib/core/cache/preview_cache.dart`、`lib/ui/pages/browser/preview_launcher.dart`、`lib/ui/pages/browser/image_preview_page.dart`
- **下载到本地 (SAF)** —— 单项菜单"下载到本地…"通过 `FilePicker.saveFile` 触发系统 SAF 另存为对话框。MVP 走整段 bytes 路径：> 50 MB 弹确认，> 500 MB 拒绝（提示用 NAS 客户端处理）。后续可换流式 SAF 写入。`lib/ui/pages/browser/preview_launcher.dart`
- **单文件上传任务** —— 新 task type `file_upload`，区别于 `folder_upload`。UI 上从浏览器 FAB 菜单"上传单文件到此处"进入，`file_picker.pickFiles` 拿 SAF URI → `UploadService.createFileUploadTask`。复用 `_uploadOne` 全部冲突/续传/rename/暂停/取消/降级逻辑。`lib/modules/upload/executor/file_upload_task_executor.dart`、`lib/modules/upload/service/upload_service.dart`、`lib/modules/upload/domain/upload_task_type.dart`、`lib/modules/upload/providers.dart`、`lib/modules/upload/upload_module.dart`
- **发起上传页"载入预设" / "另存为预设"** —— 与预设编辑页字段完全对齐（filterRegex / deleteAfterUpload / `OverwriteMode.rename`）。AppBar 加两个图标按钮：书签 = 弹出 PopupMenu 选预设（仅同 nasConfigId）一键回填所有字段；加号-书签 = 输入名后 `upsert` 当前配置为新预设。预设字段构造统一走新建的 `PresetCodec` 工具类。`lib/ui/pages/upload_launch/upload_launch_page.dart`、`lib/modules/upload/service/preset_codec.dart`、`lib/ui/widgets/upload_form_fields.dart`
- **设置页"清理预览缓存"** —— 一键清空 `<cache>/preview/` 子树，反馈清理的文件数与字节数。`lib/ui/pages/settings/settings_page.dart`、`lib/core/cache/preview_cache.dart`
- **Android 平台基建** —— FileProvider（authority `${applicationId}.fileprovider`、暴露 `cache/preview/`）；`<queries>` 声明 ACTION_VIEW 的 image/video/audio/*-* MIME（Android 11+ 包可见性）；MethodChannel `nastools/viewer.openExternal` 调用 `Intent.ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`；SAF channel 加 `createFile` / `writeBytes`（v1 暂未用，为流式 SAF 下载留接口）。`android/app/src/main/AndroidManifest.xml`、`android/app/src/main/res/xml/file_paths.xml`、`android/app/src/main/kotlin/com/nastools/app/viewer/ViewerHelper.kt`、`android/app/src/main/kotlin/com/nastools/app/saf/SafHelper.kt`、`android/app/src/main/kotlin/com/nastools/app/channel/MethodChannelRegistrar.kt`

### Changed

- **`_uploadOne` / `_findFreeName` 提升为 `UploadService.uploadOne` / `findFreeName`** —— folder/file 两类 executor 共用单文件上传核心逻辑；原 `_UploadOutcome` enum 提升为顶层 public `UploadOneOutcome`。executor `run()` 改为调 service。`lib/modules/upload/service/upload_service.dart`、`lib/modules/upload/executor/upload_task_executor.dart`
- **任务 type 字段引入 `UploadTaskType` 常量类** —— 替代裸字符串 `'folder_upload'` / `'file_upload'`，service + 两个 executor 统一引用。`lib/modules/upload/domain/upload_task_type.dart`
- **发起上传页"远端目录"由只读 Text 改为可编辑 TextField + 浏览按钮** —— 让 launch 与 preset 编辑体验完全一致；URL 注入的 `remoteRoot` 作为初始值，用户可调整后再上传或另存为预设。`lib/ui/pages/upload_launch/upload_launch_page.dart`
- **`UploadFormFields` widget 抽出** —— 把 chunkSize / 冲突策略 / wifiOnly / deleteAfterUpload / filterRegex 五项收口；预设编辑页与发起上传页共用，避免字段集合漂移。`lib/ui/widgets/upload_form_fields.dart`、`lib/modules/upload/service/preset_codec.dart`
- **`RemotePath.extension(path)` 新增** —— 取 basename 扩展名（含点，全小写）；隐藏文件视为无扩展。给 MIME 推断与预览缓存命名用。`lib/core/utils/path_utils.dart`

### Fixed

- **#1 HttpAuthInterceptor 重试丢失自签证书** —— 401 重试不再新建 Dio 实例，改用父 Dio（`attachTo(dio)` 注入），保留自签证书放行、其它拦截器、连接池。`lib/core/network/http_auth_interceptor.dart`、`lib/core/network/dio_factory.dart`
- **#2 SafHelper 子目录列举失败** —— 弃用 `DocumentFile.fromTreeUri` 链，改用 `DocumentsContract.buildChildDocumentsUriUsingTree` + `buildDocumentUriUsingTree` 显式枚举，任意层级嵌套均可递归。同时移除 `androidx.documentfile` gradle 依赖。`android/app/src/main/kotlin/com/nastools/app/saf/SafHelper.kt`、`android/app/build.gradle.kts`
- **#3 WebDAV 续传失败无降级** —— `_ResumeNotSupported` 升级为公开 `ResumeNotSupported`，`UploadTaskExecutor` 显式捕获后重新读 0 起始流整传一次。新增 `fileReported` 累计器，失败回滚精确扣除已上报字节（含失败 PUT 半途发送量），保证全局进度严格单调。`lib/adapters/webdav/webdav_adapter.dart`、`lib/modules/upload/executor/upload_task_executor.dart`
- **#4 通知权限请求无回调** —— `MainActivity` 现持有 pending `MethodChannel.Result`，`onRequestPermissionsResult` 触发后 resolve；已授予 / API<33 直接 success(true)；并发申请保护（旧 result 立即 success(false) 取消）。设置页接到结果后 SnackBar 反馈。`android/app/src/main/kotlin/com/nastools/app/MainActivity.kt`、`lib/core/background/service_bridge.dart`、`lib/ui/pages/settings/settings_page.dart`
- **`flutter pub get` / `build_runner` 工具链失效** —— 三连击修复 dev 依赖升级后的代码生成链：(1) `custom_lint ^0.6.4` 与 `riverpod_lint ^2.3.13` 通过 `analyzer` / `_macros` 间接版本不兼容，pub solver 无解 —— 升级 `custom_lint` 到 `^0.7.3`。(2) 升级后 `custom_lint_core 0.7.1` 仍把 `analyzer_plugin` pin 在 `^0.12.0`，而 0.12.0 是按 analyzer 6.x 的 `Element` API 写的，在 analyzer 7.6.0 下 `TopLevelDeclarations.publiclyExporting` 已迁到 `Element2`，build script 编译失败 —— `dependency_overrides` 强制 `analyzer_plugin: ^0.13.0`（pub backtrack 到 0.13.4，7.x 系最后一个）。(3) `drift_dev` 随之升到 2.28.0，`new_sql_code_generation` / `generate_connect_constructor` 选项已分别成为默认 / 不再必要，从 `build.yaml` 移除避免 `Unrecognized keys` 报错与 deprecation warning。`pubspec.yaml`、`build.yaml`
- **首次 `flutter run` 全链路失败** —— 四类问题一并修：(1) 国内网络下 `repo.maven.apache.org` TLS 握手不稳，`file_picker` 拉 `protobuf-java-util:3.17.2` 超时 —— `android/settings.gradle.kts` 的 `pluginManagement.repositories` 和 `android/build.gradle.kts` 的 `allprojects.repositories` 前置阿里云 mirror（`maven.aliyun.com/repository/{google,central,gradle-plugin}`）。(2) `flutter_plugin_android_lifecycle` / `shared_preferences_android` 要求 `compileSdk ≥ 36`，`sqlite3_flutter_libs` / `jni` 要求 ≥ 35 —— `android/app/build.gradle.kts` 的 `compileSdk` 从 34 提到 36（`targetSdk` 仍保留 34）。(3) `lib/modules/upload/service/local_source.dart` 第 6 行 `import 'domain/upload_chunk.dart'` 路径错误（应 `../domain/...`），但文件实际未引用 `UploadChunk` 任何符号——直接删除该死 import。(4) Flutter 3.41 起 `ThemeData.cardTheme` 参数类型从 `CardTheme` 改为 `CardThemeData`，`lib/ui/theme/app_theme.dart` 同步迁移。`android/settings.gradle.kts`、`android/build.gradle.kts`、`android/app/build.gradle.kts`、`lib/modules/upload/service/local_source.dart`、`lib/ui/theme/app_theme.dart`
- **WebDAV 测试连接对带 path 前缀的 baseUrl 全链路失效** —— 用户输入 `http://h:5005/webdav` + path `/`，原实现把 `/` 直接交给 Dio：按 RFC 3986 URI resolve，绝对 path 会**替换**整个 baseUrl 的 path，请求最终发到 `http://h:5005/`，丢掉 `/webdav` 前缀。改造分三层：(1) `webdav_client.dart` 新增 `_buildUrl(path)` 显式串接完整 URL 再交给 Dio，Dio 见绝对 URL 不再做 resolve；list/stat/head/mkcol/put/delete/move/copy/get 全部走新 helper。(2) 服务端 PROPFIND 响应里的 href 通常带前缀（`/webdav/foo`），若原样放回 `RemoteEntry.path` 会导致后续 `list("/webdav/foo")` 二次拼成 `/webdav/webdav/foo` —— `propfind_parser.dart` 接受 `stripPrefix` 参数，从 href 上剥掉 baseUrl 的 path 段，保证对外暴露的 path 永远相对于 webdav 服务根；同时把自身排除比对、href→path 转换统一抽到 `_pathOnly` helper（绝对 URL / 绝对 path 通用）。(3) `http_auth_interceptor.dart` 算 Digest 哈希时本用 `req.path`，新版传入是完整 URL 会让哈希错位 —— 抽出 `_digestUri(options)` 取 path 段（含 query）按 RFC 2617 计算。`lib/adapters/webdav/webdav_client.dart`、`lib/adapters/webdav/propfind_parser.dart`、`lib/core/network/http_auth_interceptor.dart`
- **`ViewerHelper.kt` Kotlin 编译失败** —— KDoc 注释里 `*/*` 字面量被 Kotlin 词法分析当成块注释结束符 `*/`，后续 `*` 与代码混在一起触发整片 "Expecting member declaration" 报错，`assembleDebug` 直接挂。改为用 HTML 实体 `&#42;/&#42;` 在 KDoc 里表示 `*/*`，渲染结果一致但词法上不再提前关闭注释。`android/app/src/main/kotlin/com/nastools/app/viewer/ViewerHelper.kt`
- **HTTP 401 鉴权重试永不触发** —— `DioFactory` 配置 `validateStatus: (s) => s < 500`，让 4xx 被 Dio 视为"正常响应"走 `onResponse` 路径；而 `HttpAuthInterceptor` 仅挂了 `onError`，401 永远到不了重试代码，凭据补不上，调用方直接吃到 401。修法：把重试逻辑抽到 `_retryWithAuth(response, requestOptions)` 私有方法，`onResponse` 与 `onError` 双路调用——前者是当前 dio 配置下的实际入口，后者作为兜底，应对未来若有更严格 `validateStatus` 的场景。同时把 `_DebugLogInterceptor.onResponse` 在非 2xx 时也 dump `WWW-Authenticate` / `Content-Type` / body 摘要，原先这些只在 `onError` 里打，401 走 response 看不到，挡住排查。curl 验证：服务端 `http://192.168.10.4:5005/` 在补 Basic 头后正确返回 207 Multi-Status。`lib/core/network/http_auth_interceptor.dart`、`lib/core/network/dio_factory.dart`

### Added

- **Dev-mode HTTP 调试日志** —— 排查 WebDAV 鉴权失败时缺少可见信息，仅 `kDebugMode` 下生效：(1) `DioFactory` 给每个 NAS 的 Dio 加 `_DebugLogInterceptor`，打印 `→ METHOD path  auth=Basic <redacted N bytes>` / `← status method path` / `✗ type status method path` 及响应的 `WWW-Authenticate`、`Content-Type` 头与最多 300 字节 body 摘要；Authorization 凭据按 scheme 脱敏不打实际值。(2) `HttpAuthInterceptor` 在每个决策点打印 `[auth]`：选用 Basic / Digest、用户名、Digest 的 realm / qop、重试结果状态码、失败原因。(3) `NasConfigEditPage._testConnection` catch 块把异常 + stack trace 直接 debugPrint。release 构建下三处全部无开销、无凭据外泄。`lib/core/network/dio_factory.dart`、`lib/core/network/http_auth_interceptor.dart`、`lib/ui/pages/nas_config/edit_page.dart`
- **上传预设模板** —— 把一次"文件夹上传"的全部参数（连接 / 本地 SAF URI / 远端目录 / 选项 / 过滤 / 删除策略）保存为可复用模板，列表页一键启动即创建对应 Task 入队。新增 `upload_presets` 表 + `UploadPresetDao(watchAll/getById/upsert/deleteById/touchLastRun)`；`schemaVersion` 从 1 升到 2，`MigrationStrategy.onUpgrade` 在 `from<2` 时 `m.createTable(uploadPresets)`，纯增量不动现有表；外键 `ON DELETE CASCADE` 跟随 `nas_configs`。新页面 `/presets` 列表 + `/presets/new|/presets/edit/:id` 编辑，主页 AppBar 加书签图标入口。`lib/core/database/tables/upload_presets.dart`、`lib/core/database/daos/upload_preset_dao.dart`、`lib/core/database/app_database.dart`、`lib/core/providers.dart`、`lib/ui/pages/presets/list_page.dart`、`lib/ui/pages/presets/edit_page.dart`、`lib/ui/router/app_router.dart`、`lib/ui/pages/home/home_page.dart`
- **上传文件名正则过滤** —— `UploadOptions.filterRegex`（可空）。`FolderWalker.walk/summary` 接 `filterRegex` 参数：编译为 `RegExp` 后对 basename `hasMatch`，目录无条件递归进入（否则会丢里面命中的文件）；正则非法被静默视为不过滤，executor 也照旧 walk 全量。`summary` 和 `walk` 双用同一编译结果，进度百分比准确。`lib/modules/upload/domain/upload_options.dart`、`lib/modules/upload/service/folder_walker.dart`、`lib/modules/upload/service/upload_service.dart`、`lib/modules/upload/executor/upload_task_executor.dart`
- **上传成功后删本地文件** —— `UploadOptions.deleteAfterUpload`（默认 false）。executor 区分新 `_UploadOutcome.skipped`（skip 模式命中 stat≠null）与 `done`，仅在 `done` 路径上调 `LocalSource.delete(file.uri)`；这意味着续传完成、覆盖、rename 都会触发本地删除，而"skip 因为已存在"不删。SAF 删除调 `DocumentsContract.deleteDocument`，权限层在 `persistPermission` 时已带 `FLAG_GRANT_WRITE`。被 provider 拒绝时打 warn 日志，不让任务失败。`lib/modules/upload/service/local_source.dart`、`android/app/src/main/kotlin/com/nastools/app/saf/SafHelper.kt`、`android/app/src/main/kotlin/com/nastools/app/channel/MethodChannelRegistrar.kt`、`lib/modules/upload/executor/upload_task_executor.dart`
- **远端同名重命名冲突策略** —— `OverwriteMode` 加 `rename` 枚举值。executor 进 `_uploadOne` 后若 `mode==rename` 且 `adapter.stat(remotePath)!=null`，调 `_findFreeName(adapter, path)`：拆 stem/ext 后循环 `${stem}_${n}${ext}`（n=1..9999），首个 `stat==null` 即返回；上限耗尽抛异常。后续 `mkdir/writeStream` 全部基于新名，`onChunkSent` 计数沿用原文件大小，进度不受影响。UI 端冲突下拉新增"远端已存在则重命名"选项。`lib/modules/upload/domain/upload_options.dart`、`lib/modules/upload/executor/upload_task_executor.dart`、`lib/ui/pages/presets/edit_page.dart`

### Changed

- **UI 文案统一：NAS → 连接** —— 为后续接入 SFTP / SMB 等多协议留余地，把 UI 中的"NAS"字样全部改为"连接"：主页空态标题、主页 FAB、NAS 配置列表 AppBar、列表空态标题、删除确认对话框、编辑页 AppBar、自签证书 subtitle 共 8 处。代码层（变量名、类名、错误消息、注释、URL 路径如 `/nas`、表名 `nas_configs`）保持不变，避免兼容性风险。`lib/ui/pages/home/home_page.dart`、`lib/ui/pages/nas_config/list_page.dart`、`lib/ui/pages/nas_config/edit_page.dart`

- **#8 Drift 表添加索引** —— `tasks(status)` + `tasks(moduleId, type)`、`task_chunks(taskId)`、`logs(ts)` + `logs(taskId)`。覆盖 `watchActive` / `watchByStatus` / 任务-分片关联 / 日志清理等热路径。`lib/core/database/tables/*.dart`
- **#10 TaskDao 使用 TaskStatus 常量** —— 去掉硬编码 `'waiting'/'running'/'paused'` 字面量，统一引用 `TaskStatus.waiting` 等。`lib/core/database/daos/task_dao.dart`
- **README 强调"首次拉起：必跑三步"** —— `flutter pub get` + `flutter create --platforms=android .` + `dart run build_runner build`，加 ⚠ 警示放到醒目位置。`README.md`

## [0.1.0] - 初始骨架

### Added

- 模块化单体架构：`core/` + `adapters/` + `modules/` + `ui/`
- Drift 数据库（5 表 / 4 DAO）：`nas_configs` / `tasks` / `task_chunks` / `file_index` / `logs`
- TaskManager：状态机（waiting/running/paused/failed/completed/cancelled）、`ConcurrencyGate`、`RetryPolicy`（指数退避+jitter+4xx 不重试）、启动时 `reviveInterrupted`
- 协议抽象 `StorageAdapter`，WebDAV 完整实现（PROPFIND / MKCOL recursive / PUT / HEAD / DELETE / MOVE / COPY / GET range）
- 鉴权：HTTP Basic + Digest 拦截器
- 自签证书放行：按 NAS 配置独立放行 host
- Upload 模块：`UploadService` / `FolderWalker` / `UploadTaskExecutor`，支持 skip / overwrite / resumeOrOverwrite 三种冲突策略
- Riverpod 分层 providers（L0 基础 / L1 Repository / L2 Service / L3 ViewModel）
- go_router 路由骨架
- UI 页面：Home、NAS 配置列表/编辑（含连接测试）、远端浏览器、上传发起页、任务中心（3 tab + 暂停/恢复/取消/重试）、设置页
- Android Kotlin 层：Foreground Service（dataSync 类型，WakeLock，进度通知）、SAF MethodChannel、电池优化白名单引导
- `flutter_background_service` / `flutter_local_notifications` / `permission_handler` —— 后两者最终未使用，已从 pubspec 移除
