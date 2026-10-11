# 实施计划草案

## 阶段 1：规划与原型（当前）

- [x] 记录用户选择 Concept 01、批准规划及补充开发规则。
- [x] 阅读现有规范、旧任务范围、父任务及页面/业务代码。
- [x] 写入原型优先规范及跨规范优先级，记录迁移尚未实施。
- [x] 产出初步页面/能力差异表、PRD、技术设计草案。
- [x] 用户选择 A：现有功能 UI/导航迁移；新能力后续规划，已解除父任务的排序筛选前置依赖。
- [x] 范围约束：跟随系统主题、不新增远端缩略图；已有远端媒体预览保留。
- [x] 用户采用连接选择规则：会话内恢复显式选择；未选时选择、无连接新建、删除回选择、断网保留并重试，顶部提供切换入口。
- [x] 用户采用子页面导航规则：底部栏仅四个主页面显示；子页面隐藏并返回实际来源、保留来源状态。
- [x] 用户采用未保存编辑保护：表单有改动时返回先确认；保存/删除成功后不提醒，测试连接不算改动。
- [ ] 细化原型缺口清单及状态覆盖表，用户确认后进入原型补充实施。
- [ ] 完成 PRD 收敛与最终计划评审。填充并验证 JSONL。
- [ ] 用户批准实施后执行 `python .trellis/scripts/task.py start .trellis/tasks/10-01-ui-polish`。

> 当前不调用 start：本仓库 start 会把 planning 改为 in_progress。复用任务路径开展规划，不伪造 active/in_progress 状态。

## 阶段 2：原型补充与门槛

- [x] 按范围 A 补充 HTML 场景：连接选择页、子页导航、未保存保护、完整上传配置（文件/文件夹、五+四策略、移动上传、保存预设）、批量删除、任务警告、不定进度、首次/刷新失败区分、多层目录、长文件名；隔离探索版新增能力（无搜索/筛选/重命名/主题入口，类型图标替代缩略图）。
- [x] 原型新增场景冒烟测试通过；明暗/移动截图已更新。
- [x] 用户确认 v1.1 基准（BASELINE.md 变更清单）；任务已激活（in_progress）。

> 阶段 1、2 完成。进入阶段 3 正式迁移。

## 阶段 3：正式迁移（建议顺序）

1. [x] 主题角色、字号/间距/形状、状态色与公共反馈组件，更新 token 规范。（主会话内实施：子代理派发因模型 API 负载上限两次失败；compileDebugKotlin + testDebugUnitTest 用本机 Gradle 8.14 通过，TaskStatusUiTest 等全部通过。改动：Color.kt 全套明暗值 + Progress/Danger 专用语义色、StatusColors.kt 五档专用色、Shapes.kt Card 18/Field 12/Badge 6 + 阶梯 18/24、AppChrome 顶栏去分隔线/空态裸图标/卡片 flat+描边辅助/徽章去描边阴影、design-tokens.md 同步。Spacing 阶梯未动，页面级内边距在各页迁移时对齐。）
2. [x] 主导航容器、四入口、当前连接选择与恢复；现有子路由兼容。（主会话内实施：子代理第三次派发仍因 API 负载失败。改动：新增 FileTabSession（会话内选择+目录恢复，不持久化）、FilesScreen/FilesViewModel（选择页/浏览器切换、配置删除清理）、ConnectionSelectScreen、NasBottomBar+NasBottomNavItem（AppChrome）、NasScaffold 增加 bottomBar 槽位；NavGraph 重构为 home/files/tasks/settings 四入口 + navigateTab（singleTop+popUpTo saveState/restoreState），移除 browser/{id} 路由；HomeScreen 卡片点击改为 selectConnection+进文件标签、顶栏去冗余图标；TasksScreen/SettingsScreen 改为标签页（去返回箭头）；BrowserScreen 去 configId/onBack 参数、顶栏加切换连接入口、目录级返回保留；BrowserViewModel 由会话选择驱动加载并持久化目录。compileDebugKotlin + testDebugUnitTest 通过。）
3. [x] 连接首页与连接编辑（测试/保存/删除、级联文案）。（主会话内实施。首页：欢迎语、绿色传输横幅（真实任务数据、未知总量不定进度、点击进详情）、卡片原型布局（图标+名称+主机+菜单、底部认证+浏览文件）、常用工作流预设入口、顶栏 + 新建、去 FAB；编辑页：未保存保护（isDirty 任意改动即标记、测试连接不算、保存成功复位、顶部+系统返回均拦截）、级联删除文案对齐原型、表单标签上方+帮助紧邻、并排测试/保存按钮；NasConfirmDialog 增加 dismissText 参数（默认「取消」向后兼容）。偏差记录：删除入口保留在编辑页顶栏（原型在卡片菜单，功能等价）。compileDebugKotlin + testDebugUnitTest 通过。）
4. [x] 文件浏览、上传配置、原有媒体能力，保留当前目录/SAF/冲突/移动上传契约。（主会话内实施。浏览器：面包屑多层目录（根目录+逐级可点）、列表模式文件夹两列卡片+文件裸行分隔线、网格模式保留、视图切换移到面包屑行、顶栏收敛为 切换/刷新/+菜单（上传文件/上传文件夹/新建文件夹）、底部「上传到当前目录」入口；上传对话框：来源/目标信息、五+四策略、移动上传、保存预设、默认策略按远端大小说明；EntryIcon 全部 tint 气泡。偏差记录：原型文件夹图标为橄榄色未入 token，用 primary 代替；上传目标固定为当前目录（按钮文案已说明），任意目标目录留待后续；媒体页已用共享组件保持不动。compileDebugKotlin + testDebugUnitTest 通过。）
5. [x] 任务列表与详情，批量选择/删除、六态及警告/错误明细。（主会话内实施。任务卡：总量未知→不定进度+「总计大小未知」、字节格式改 BytesFormat、跳过/过滤警告琥珀色（与失败红区分）、控制行提示文案；详情：120dp 进度环（未知总量→不定进度+「进度未知」）、警告卡琥珀色、删除确认文案对齐「只删记录不删 NAS 文件」；批量删除确认文案同步。偏差记录：批量选择保留在已完成/失败标签（既有能力范围，原型未明确活跃标签多选）；速度/剩余时间真实数据未知，不展示（R8）。compileDebugKotlin + testDebugUnitTest 通过。）
6. [x] 预设列表/编辑、系统设置；串联全部入口。（主会话内实施。预设卡：原型布局（图标+名称+连接、来源→目标路径块、策略标签+运行按钮、菜单编辑/删除）；预设编辑：未保存保护（isDirty+顶部/系统返回确认，保存成功复位）、副标题改「上传工作流」；设置页：品牌头、工作流组（上传预设入口→预设列表）、后台传输组（真实权限徽章，未授权/待设置改 Warning 琥珀）、关于组；NavGraph 串联 settings→presets。冲突选项列表改 internal 供同包共享。compileDebugKotlin + testDebugUnitTest 通过。）
7. [x] 检查范围 A 边界：不实现新增搜索/类型筛选/重命名/手动主题偏好/远端缩略图加载，不显示空功能按钮、不假装完成关联任务。（检查完成：BrowserScreen 无搜索框/筛选 chips/重命名菜单；SettingsScreen 无手动主题入口；文件行用类型图标无缩略图；媒体预览保留既有远端能力；所有可见按钮均有真实行为。）

每步：派发实现 → 检查 → 修复 → 记录可验证结果。全部使用当前真实任务路径作为子代理 prompt 第一行。当前不派发无 active task 的执行子代理。

## 阶段 4：验收

- [x] 逐条勾选 PRD AC，审阅 research 对照表，旧功能无遗漏。（代码层核查完成：AC1 规范/基线/记录齐备；AC2 全页面迁移+集中 token；AC3/AC4/AC5 代码层成立；AC6/AC8 需设备目视验收，标记待验收；AC7 编译+单测+lint 通过，受控 NAS 回归待设备。）
- [x] JVM 状态/路由/上传 options 回归；Compose 导航/确认与选择交互测试。（testDebugUnitTest 全部通过含 TaskStatusUiTest/BrowserUiStateTest/UploadWarningsTest；Compose 交互测试需设备，待验收。）
- [ ] 导航覆盖：四主页面显示底部栏，子页面隐藏；设置→预设→返回设置、文件→图片→返回原目录位置、任务列表→详情→返回原分组位置。（代码层已实现并静态核对；真机交互验收待设备。）
- [ ] 未保存保护覆盖：连接/预设表单改动后顶部与系统返回均弹确认；取消留在页面；保存/删除成功返回不提醒；未改动直接返回不提醒；测试连接不触发。（代码层已实现；真机验收待设备。）
- [ ] 连接选择覆盖零/一/多连接、已选目录恢复、主动切换、当前配置删除、配置读取失败/断网；断言无静默换 NAS、无旧目标待操作跨连接执行。（代码层已实现 FilesViewModel 清理链路；真机验收待设备。）
- [ ] 320dp/390dp/宽屏、字体 1.0/1.3/2.0、明暗、键盘、系统返回、长路径。（需设备，待验收。）
- [ ] 受控 NAS 验证认证、读取、刷新失败、上传/续传/跳过、媒体 Range 与生命周期。（需设备与 NAS，待验收。）
- [ ] 用户目视确认与操作验收；没有真机/模拟器则保留未验收项。（待用户执行。）

### 质量核查记录（主会话内执行，子代理第 4 次派发失败）

- compileDebugKotlin + testDebugUnitTest + lintDebug 全部通过（本机 Gradle 8.14）。
- 无遗留冲突标记；无意外文件改动（.opencode/package.json 保持用户原样）。
- 裸 dp 抽查：仅 AppChrome NasMiniFab 的 FAB elevation 字面量（组件固有尺寸，类别③合规）。
- 共享组件调用方兼容：NasConfirmDialog 8 处调用（dismissText 向后兼容）；子路由均不传 bottomBar。
- FileTabSession 清理链路：FilesViewModel 观察配置列表，删除当前连接→clearSelection→回选择页。
- 范围 A 边界：无搜索/筛选/重命名/主题入口/缩略图。

## 验证命令

```bash
python .trellis/scripts/task.py validate .trellis/tasks/10-01-ui-polish
node --check prototypes/nastools-ui/app.js
# 需要本机已有 Playwright + Chrome；不往 Android 工程新增 npm 构建依赖
node prototypes/nastools-ui/smoke-test.cjs
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew assembleRelease
# 有设备后再执行；具体 instrumentation 配置在实施阶段核对
./gradlew connectedDebugAndroidTest
```

命令只是计划，未运行不得勾为通过。此前 Gradle 下载出现 Connection reset，实施前需重新核实 JDK/SDK/网络，不将旧环境结论当作现状。

## 风险与回滚点

- `NavGraph.kt`：现为逐路由进入，新增主入口不可破坏 configId/media 路径传递和系统返回。
- `AppChrome.kt` 与 theme：共享改动影响全应用，必须同时检查调用方，不能只看首页。
- 上传/预设表单：尤其移动上传和同名策略不可被原型简化覆盖。
- 原型 `MOVE` 演示、系统权限、密码、速度均不是生产逻辑。
- `.opencode/package.json` 是用户已有无关修改，禁止混入。
- 范围 A 已确认，但详细规划未完成；不提前创建并行实施分支，不将本轮迁移视为父任务其他子任务已完成。
