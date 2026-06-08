# NasTools - 个人 NAS 工具箱

Android 优先的 Flutter 模块化 NAS 工具集合。
本次版本实现：WebDAV 连接管理、文件夹 / 单文件上传（断点续传 / 正则过滤 / 上传后删本地 / 同名重命名）、WebDAV 文件管理（增删改查 / 多选 / 视图切换）、图片视频在线预览（外部播放器）、上传预设模板、Android 后台 Foreground Service、任务中心。

变更历史见 [CHANGELOG.md](./CHANGELOG.md)。

## 环境准备 (Windows)

### 1. 安装 Flutter SDK

下载并解压到任意目录（例如 `C:\flutter`），并把 `C:\flutter\bin` 加入 `PATH`：

- 推荐：从官网下载稳定版 `flutter_windows_*-stable.zip`
- 解压后在 PowerShell 验证：`flutter --version`

### 2. 安装 JDK 17

推荐 Microsoft Build of OpenJDK 17 或 Eclipse Temurin 17。
设置环境变量 `JAVA_HOME` 指向 JDK 目录。

### 3. 安装 Android Studio

- 安装 Android Studio（用于 Android SDK + 模拟器）
- 首次启动时安装 Android SDK Platform 34 (Android 14)、SDK Build-Tools 34、Platform-Tools
- 接受 license：`flutter doctor --android-licenses`

### 4. 验证环境

```powershell
flutter doctor -v
```

确保：
- [v] Flutter
- [v] Android toolchain
- [v] Connected device (USB 真机或模拟器)

## 首次拉起：必跑三步

> ⚠ 三步顺序不能省，缺一会编译失败。

```powershell
# 1. 拉依赖
flutter pub get

# 2. 补齐 Flutter 项目缺失资源（启动图标 / Gradle wrapper 等）—— 不会覆盖已有文件
flutter create --platforms=android --project-name=nastools --org=com.nastools .

# 3. 生成 Drift / Riverpod / Freezed 的 *.g.dart / *.freezed.dart
dart run build_runner build --delete-conflicting-outputs
```

之后若改动了带 `@freezed` / `@DriftDatabase` / `@riverpod` 注解的源文件，重跑步骤 3 即可：

```powershell
dart run build_runner watch --delete-conflicting-outputs   # 监听模式
```

## 运行

```powershell
flutter run                                  # 默认设备
flutter run -d <device-id>                   # 指定设备
flutter run --release                        # release 模式
```

## 项目结构

```
lib/
├── core/             # 数据库、Task Manager、Logger、网络、插件系统、后台服务桥
├── modules/          # 业务模块（upload / ssh / file_manager / dedup ...）
├── adapters/         # 协议适配器（webdav / sftp / smb / local）
└── ui/               # 页面、路由、主题、widgets

android/app/src/main/kotlin/com/nastools/app/
├── service/          # Foreground Service
├── saf/              # SAF 树 URI 帮手
├── battery/          # 电池优化白名单引导
└── channel/          # MethodChannel 注册
```

## 第一次使用

1. 启动 App，授权通知权限和 SAF 树 URI
2. 进入「连接管理」→ 新建 → 选择 WebDAV 类型
   - 例：群晖 WebDAV `http://your-nas:5005`（HTTP）或 `https://your-nas:5006`（HTTPS）
   - 用户名 / 密码
   - HTTPS 自签证书可勾选「信任」
3. 点「测试连接」→ 成功后保存
4. 回到首页 → 点击连接卡片进入远端文件浏览器
5. 进入想上传到的目录 → 右下 FAB「上传」→ 选「上传文件夹到此处」或「上传单文件到此处」
6. 选择本地路径（SAF）→ 启动上传
7. 任务中心查看进度，可暂停/恢复/取消

### 远端文件管理

浏览器页同时也是 WebDAV 管理器：

- **视图切换**：右上 grid/list 图标切换列表与网格视图，跨 session 记住选择
- **单项动作**：列表右侧 `⋮` 或网格右上 `⋮` → 弹底部菜单：重命名 / 移动到… / 复制到… / 下载到本地… / 删除（"移动"和"复制"会再次弹文件夹选择器选目标）
- **多选**：长按任意一项进入多选；底部条出现「移动」「删除」按钮；选中数为 0 自动退出
- **图片视频预览**：点击文件 → 自动下载到 app 缓存 (`<cache>/preview/<configId>/...`) → 图片用内置全屏页（缩放/平移），其它类型调系统外部应用打开
- **下载到本地**：单项菜单"下载到本地…"用系统 SAF "另存为"对话框保存任意目录；MVP 限制最大 500 MB，>50 MB 弹确认
- **预览缓存清理**：设置页 →「清理预览缓存」一键清空 `<cache>/preview/` 子树

### 上传预设（常用配置一键启动）

把高频的"本地文件夹 → 远端目录"组合存为模板，后续无需重选参数：

1. 主页顶栏点书签图标进入「上传预设」
2. 「新建预设」→ 填名称、选连接、选本地文件夹、点远端目录字段右侧「📂」按钮**从远端文件夹选择**目标路径
3. 可选填**文件名正则**（如 `\.(jpg|jpeg|png)$` 只上传图片）；目录始终递归进入
4. 可选打开**「上传成功后删本地文件」**——单文件上传成功立即删；skip 模式（远端已存在则跳过）不会删本地
5. 「冲突处理」选「远端已存在则重命名」时，会自动选 `name_1.ext` / `name_2.ext` 等首个不冲突的名字上传
6. 保存后在预设列表点「运行」即创建一个新 Task，跳到任务中心看进度

发起上传页（从浏览器 FAB "上传文件夹"进入）也支持同一组字段。AppBar 右上：
- **书签 📑**：从已有预设一键载入所有字段（仅显示同 NAS 的预设）
- **加号-书签 ➕📑**：把当前发起页的配置另存为一个新预设

## 故障排查

- **build_runner 报冲突**：加 `--delete-conflicting-outputs`
- **Android 13+ 没有通知**：在设置页点「通知权限」—— 若用户已拒绝，SnackBar 会提示「后台进度通知将不可见」，需到系统设置手动开启
- **后台被冻结**：设置页 →「电池优化」→ 加入白名单
- **WebDAV 401**：检查是否启用了 Basic / Digest 认证，密码字符是否含特殊字符
- **HTTPS 自签连接失败**：在连接配置勾选「信任自签证书」
- **续传看着像从头开始**：服务端可能不支持 `Content-Range`，应用会自动降级整传一次（日志页可见 `[warn] upload: ... 降级整传`），下次该文件就走整传路径
- **上传到深层目录跑不动**：需要确保 SAF 权限是「整个文件夹」而非单文件 —— 用 `getDirectoryPath` 选择根目录
- **预设运行后任务立刻 failed**：极可能是 SAF 树 URI 权限被撤销（系统设置→App→存储清理过），重新打开 App 让它再次请求 SAF；或编辑预设重新选一遍本地文件夹
- **「上传后删本地」开了但本地文件没删**：日志会有 `[warn] upload: ... 本地删除被拒绝`——SAF provider 拒绝（多见于 MediaStore 一类位置），文件不会被强删；可手动清理或换用「整个文件夹」级别的 SAF 授权

## 已实现功能

- [x] WebDAV 连接管理（多 NAS）
- [x] 远端文件浏览（list / grid 视图切换，跨 session 保留）
- [x] 远端文件管理：重命名 / 移动 / 复制 / 删除（单项 + 多选批量）
- [x] 文件夹上传（递归）
- [x] 单文件上传（与文件夹共用断点续传 / 重命名等所有特性）
- [x] 图片 / 视频在线预览（下载到 cache + 内置 PhotoView / 外部应用 ACTION_VIEW）
- [x] 下载远端文件到本地（SAF "另存为"，MVP 限 500 MB）
- [x] 断点续传（Content-Range，回退整传）
- [x] 后台上传（Android Foreground Service）
- [x] 任务中心（暂停/恢复/取消/重试）
- [x] 通知栏进度
- [x] SQLite 持久化
- [x] 上传预设模板（一键复用 / 载入 / 另存为）
- [x] 上传文件名正则过滤
- [x] 上传成功后自动删本地（SAF deleteDocument）
- [x] 远端同名重命名（`name_1.ext` / `name_2.ext`）
- [x] 远端目录通过文件夹选择器（不再手动输入路径）

## 未实现（占位）

- [ ] SFTP / SMB 适配器
- [ ] SSH 模块
- [ ] 文件去重 / 大文件分析
- [ ] Docker 管理
- [ ] 预设的定时 / 触发型自动执行
- [ ] 大文件下载流式 SAF 写入（突破 500 MB 上限）
- [ ] iOS / 桌面平台

## 协议

私有使用。
