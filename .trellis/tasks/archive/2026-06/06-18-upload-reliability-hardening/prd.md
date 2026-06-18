# 上传可靠性与产品闭环

## Goal

把当前已经可用的 NAS 浏览、上传、任务中心和预设链路做成更可靠、可回归的第一阶段产品闭环。优先修复上传执行器中的并发进度与长任务稳定性风险，补齐自动化验证，然后处理危险操作确认、安全存储和文档状态同步。

## What I already know

* 当前主干 `:app:compileDebugKotlin` 和 `:app:lintDebug` 均通过。
* 应用已具备首页连接列表、WebDAV 配置、文件浏览器、文件/文件夹上传、任务中心、任务详情、上传预设、设置页和前台服务。
* 近期任务连续修复过文件夹上传无响应、上传后删除失败误判失败、上传性能、UI 流畅度、内存和启动性能。
* `UploadExecutor` 是当前复杂度最高的模块，包含文件夹递归、并发上传、冲突策略、断点续传、删除本地源、Wi-Fi 限制和任务标题动态更新。
* `.trellis/spec/backend/concurrency-patterns.md` 要求并发上传使用原子递增和串行化进度回调；当前实现用多个文件共享 `AtomicLong.set(newValue)`，存在并发进度覆盖风险。
* `NasForegroundService` 当前 wake lock 超时为 10 分钟，长视频/相册备份可能超过该边界。
* `NasConfigEntity.passwordEncrypted` 当前实际保存明文密码，命名和行为不一致。
* `AndroidManifest.xml` 全局 `usesCleartextTraffic="true"`，需要收敛风险或在 UI 中明确提示。
* 远端浏览器删除、任务列表单条删除、预设删除等不可撤销操作的确认行为不一致。
* README / PROJECT_STATUS 中部分功能状态已经过期。
* 当前仓库没有 `app/src/test` 或 `app/src/androidTest` 测试目录。

## Assumptions (temporary)

* 第一阶段优先确保 WebDAV 上传可靠，不在本任务引入 SFTP/SMB。
* 第一阶段以单元测试和可运行的 Gradle 检查为主，真机 NAS 兼容性留手动回归清单。
* 安全治理先做低风险增量：明确明文风险、限制/提示 HTTP、自签名证书边界；完整 Keystore 迁移可拆为后续任务。

## Open Questions

* 已决：用户要求“按你的计划进行开发”。本任务采用推荐 MVP，优先上传可靠性和测试；安全、删除确认、文档同步做最小必要闭环；SFTP/SMB、下载管理器和大 UI 改版排除。

## Requirements (evolving)

* 修复文件夹并发上传时的全局进度聚合，避免多个文件互相覆盖进度。
* 修正上传读取到 EOF 但未达到预期大小时仍按完成处理的风险。
* 为上传执行器和任务管理器补可运行的单元测试，覆盖成功、失败、跳过、删除失败警告、并发进度。
* 梳理长任务后台边界：wake lock、前台通知、网络切换和任务恢复。
* 统一不可撤销删除操作的确认策略。
* 梳理凭据和明文 HTTP 风险，至少消除命名误导并给出用户可见提示或技术迁移路径。
* 更新 README / PROJECT_STATUS，使文档反映当前功能状态。

## Acceptance Criteria (evolving)

* [ ] 多文件并发上传进度单调递增，不因并发文件回调覆盖而倒退或少计。
* [ ] 本地文件读取短于预期续传/上传长度时，任务进入 `failed` 且可重试。
* [ ] 上传成功但删除本地源失败时，任务仍为 `completed` 并带非致命警告。
* [ ] 任务失败场景仍按 fatal error 进入 `failed`，不被误吞为完成。
* [ ] 至少新增 `UploadExecutor` / `TaskManager` 关键行为测试。
* [ ] `:app:compileDebugKotlin`、`:app:lintDebug` 和新增测试通过。
* [ ] 危险删除操作行为一致，有确认或明确的撤销/反馈策略。
* [ ] 文档不再把已实现的浏览器、上传、配置、设置、预设和前台服务标为待实现。

## Definition of Done

* 测试新增或更新，覆盖上传关键状态分支。
* Kotlin 编译、lint、单元测试通过。
* 行为变化有文档或 PRD 记录。
* 不引入 SFTP/SMB 等大协议面扩展。
* 工作树只包含本任务相关改动。

## Out of Scope

* SFTP/SMB 实现。
* 完整下载管理器和下载任务中心。
* 远程文件预览。
* 大规模 UI 改版。
* 完整 Keystore 数据迁移如果风险过高，可拆后续任务。
* 真机性能基准自动化和 Macrobenchmark。

## Technical Notes

* 主要代码路径：
  * `app/src/main/java/com/nastools/app/service/UploadExecutor.kt`
  * `app/src/main/java/com/nastools/app/service/TaskManager.kt`
  * `app/src/main/java/com/nastools/app/service/NasForegroundService.kt`
  * `app/src/main/java/com/nastools/app/data/network/WebDavClient.kt`
  * `app/src/main/java/com/nastools/app/presentation/browser/BrowserScreen.kt`
  * `app/src/main/java/com/nastools/app/presentation/tasks/TasksScreen.kt`
  * `app/src/main/java/com/nastools/app/presentation/presets/PresetListScreen.kt`
  * `app/src/main/java/com/nastools/app/presentation/config/ConfigEditViewModel.kt`
* Relevant specs:
  * `.trellis/spec/backend/error-handling.md`
  * `.trellis/spec/backend/concurrency-patterns.md`
  * `.trellis/spec/frontend/compose-optimization.md`
* Verification already run before task creation:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`

## Expansion Sweep

### Future evolution

* 上传可靠性测试可成为后续 SFTP/SMB、下载任务、相册自动备份的基础合同。
* 凭据治理后续可演进为 Android Keystore + 数据库迁移。

### Related scenarios

* 浏览器删除、任务删除、预设删除、连接删除都属于不可撤销操作，应保持一致。
* 上传预设和浏览器即时上传应共享同一组选项语义。

### Failure / Edge Cases

* 网络超时、认证失败、远端冲突、短读、权限丢失、删除失败、进程重启、非 Wi-Fi 网络都需要清晰归类。

## Decision (ADR-lite)

**Context**: 项目已经完成第一轮产品链路和性能优化，但上传执行器承担了太多高风险逻辑，且缺少测试保护。

**Decision**: 第一阶段不扩新协议，先把 WebDAV 上传主链路做成可回归的稳定核心，再处理安全、危险操作和文档同步。

**Consequences**: 短期新功能速度放慢，但能降低后续功能叠加时的回归概率。
