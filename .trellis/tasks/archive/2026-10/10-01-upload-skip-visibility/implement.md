# 执行计划 — 同名文件跳过可见化

对应 `prd.md`（R1–R4、AC1–AC8）与 `design.md`（§1–§6）。按顺序执行。

---

## 0. 基线

- [ ] `0.1` 确认工作区干净（除 Trellis 工具链改动外）：
  ```bash
  git status --short -- app/
  ```
  预期：无输出（上一轮 token 迁移已提交）。

- [ ] `0.2` 基线编译与测试（**必须联网**，`--offline` 因 aapt2 未缓存会失败）：
  ```bash
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
  ```
  预期 `EXIT=0`，`TaskStatusUiTest` 6/6、`UploadProgressTrackerTest` 5/5。

---

## 1. 引入 SkipLog

- [ ] `1.1` 在 `service/UploadExecutor.kt` 定义 `private class SkipLog`（`design.md` §2.1），
  放在 `FileUploadPlan` 定义（文件末尾 :676 附近）之前或之后，同文件内。
  两个列表均用 `Collections.synchronizedList` —— 文件夹路径下 `uploadFile` 并发执行。

- [ ] `1.2` `execute()`（`:110`）内 `val warnings = mutableListOf<String>()`（`:131`）旁
  创建 `val skips = SkipLog()`。

---

## 2. 收集跳过

- [ ] `2.1` `uploadFile()` 签名（`:365` 附近）新增 `skips: SkipLog` 参数。

- [ ] `2.2` `filterRegex` 分支（`:381-384`）在 `markComplete` 前加 `skips.filtered.add(localName)`。

- [ ] `2.3` 同名跳过分支（`:394-397`）在 `markComplete` 前加 `skips.sameName.add(localName)`。
  **该分支同时服务 `skip_existing` 与默认模式的「大小相等」去重，两者都经此记录 —— 不要**
  **只在 `plan.skip` 的来源处分支处理。**

- [ ] `2.4` 补全 `uploadFile` 的三个调用点，传同一份 `skips`：
  - `execute()` 单文件分支（`:142` 附近）
  - `uploadFolder()` 签名与 `uploadDirectory()` 签名各加 `skips: SkipLog`
  - `uploadDirectory` 内调用 `uploadFile` 处（`:315` 附近）

---

## 3. 汇总输出

- [ ] `3.1` `uploadFolder()` 末尾：与既有 `skippedFolders` 汇总并列，加
  `warnings.addAll(skips.toWarnings())`。注意该方法有**两处** return
  （`:210`/`:212` 附近的提前 return 与 `:229` 附近的正常 return），**两处都要加**。

- [ ] `3.2` `execute()` 单文件分支：在 `finally { taskRepository.updateTitle(...) }` 之后、
  `formatUploadWarnings(warnings)` 之前加 `warnings.addAll(skips.toWarnings())`。
  **只在 else 分支加** —— 文件夹分支已由 3.1 覆盖，重复加会输出两遍。

- [ ] `3.3` 自检：确认 `skips.toWarnings()` 恰好被调用两次（各路径一次），
  ```bash
  grep -n "toWarnings()" app/src/main/java/com/nastools/app/service/UploadExecutor.kt
  ```

---

## 4. 展示端收尾

- [ ] `4.1` `presentation/tasks/TasksScreen.kt:361-364` 的
  `Text(task.errorMessage, style = …, color = …)` 加 `maxLines = 2` 与
  `overflow = TextOverflow.Ellipsis`；若未 import 则补 `androidx.compose.ui.text.style.TextOverflow`。

---

## 5. 单元测试（AC7）

- [ ] `5.1` 新建 `app/src/test/java/com/nastools/app/service/UploadWarningsTest.kt`，
  针对 `formatUploadWarnings()`（`internal`，`:89`）与 `SkipLog.toWarnings()`。

  `SkipLog` 当前是 `private class` —— **改为 `internal class`** 以便测试访问
  （与 `formatUploadWarnings` 现有的 `internal` 一致）。这是必要的可见性调整，
  不改变运行时行为。

  断言点：
  1. 空 `SkipLog` → `toWarnings()` 返回空列表（保证 AC4：正常上传不多出警告）。
  2. 3 个同名跳过 → 文案含「3 个同名文件」及三个文件名。
  3. 12 个同名跳过 → 含前 10 个文件名、不含第 11 个、且以 ` …` 结尾。
  4. 过滤规则跳过 → 独立的「未匹配过滤规则」文案，且与同名文案**不互相合并**。
  5. `formatUploadWarnings(emptyList())` → `null`。
  6. `formatUploadWarnings(listOf("跳过 3 个同名文件：a、b、c"))` → 原样输出，
     不被「本地」类汇总吞并。
  7. `formatUploadWarnings` 含「本地…」警告 + 跳过警告 → 两者都出现（前者被折叠成计数句）。

- [ ] `5.2` 运行：
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
  预期全绿。

---

## 6. 全量验证

- [ ] `6.1` 编译 + 双包：
  ```bash
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  ```
  预期 `EXIT=0`（AC8）。

- [ ] `6.2` 回归核对 AC4：`git stash` 前后对比「无跳过时的警告串」不适用（无法在无设备下跑）。
  改为**以单元测试断言代偿**（5.1 的第 1、6 条），并在报告中明确说明这是代偿而非端到端验证。

- [ ] `6.3` 提交前版本号追加第三级：`versionName 0.2.0 → 0.2.1`，`versionCode 2 → 3`。

- [ ] `6.4` **停下交用户目视**（AC1/AC2/AC5/AC6）：装 `app-release.apk`，
  对一个远端已存在同名文件的目录跑上传，确认详情页出现跳过提示、列表卡片被截断为 2 行。

- [ ] `6.5` 用户确认后进入 Phase 3：`trellis-check` → spec 更新 → 提交 → 推送。

---

## 验证命令汇总

| 目的 | 命令 |
|---|---|
| 编译 + 双包 + 测试 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` |
| 测试报告 | `grep -h "<testsuite " app/build/test-results/testDebugUnitTest/*.xml` |
| 汇总调用点 | `grep -n "toWarnings()" app/src/main/java/com/nastools/app/service/UploadExecutor.kt` |
| 签名验证 | `<sdk>/build-tools/34.0.0/apksigner.bat verify --print-certs app/build/outputs/apk/release/app-release.apk` |

> 验证 APK 签名**不要用 `jarsigner`** —— 它只看 v1，而 AGP 默认关闭 v1 只出 v2，会误报未签名。

---

## 风险与回滚点

| ID | 风险 | 应对 |
|---|---|---|
| R-1 | 汇总被输出两遍（文件夹路径经两个调用点） | 步骤 3.2 限定只在 else 分支输出；3.3 自检确认恰好两处 |
| R-2 | `SkipLog` 用非线程安全列表，并发下丢失或抛异常 | `Collections.synchronizedList`，与既有 `fileWarnings`（:307）一致 |
| R-3 | `uploadFolder` 的提前 return 分支漏加汇总 | 步骤 3.1 明确要求两处都加 |
| R-4 | `SkipLog` 改 `internal` 后意外扩大 API 面 | 仅可见性调整，类仍为文件内私有语义；确认未被 `presentation` 引用 |
| R-5 | 正常上传多出警告（AC4 回归） | 单元测试第 1、6 条断言空 SkipLog 不产出文案 |

**回滚**：改动集中在 `UploadExecutor.kt` 与 `TasksScreen.kt` 一处属性，单次 `git revert` 可整体回退。
