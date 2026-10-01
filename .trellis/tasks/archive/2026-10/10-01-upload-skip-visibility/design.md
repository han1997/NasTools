# 技术设计 — 同名文件跳过可见化

对应 `prd.md` 的 R1–R4、AC1–AC8。

---

## 1. 边界与不变量

**改动范围**：`app/src/main/java/com/nastools/app/service/UploadExecutor.kt`（主要）
与 `app/src/main/java/com/nastools/app/presentation/tasks/TasksScreen.kt`（一处展示收尾），
外加新增测试 `app/src/test/java/com/nastools/app/service/UploadWarningsTest.kt`。

**不变量**

1. **默认冲突策略不变**：仍是 `resume_or_overwrite`。本任务只增加「记录」，不改「决策」。
   `prepareFileTarget()`（`:509-542`）的判分派逻辑一行不动。
2. `execute()` 的返回值契约不变：仍是 `String?`（警告串或 null）。
3. 正常（无跳过）上传产生的警告串与改动前**逐字节相同** —— 这是 AC4 的回归防线。
4. 上传并发度不变（文件夹仍为 `Semaphore(3)`）。

---

## 2. 数据收集：SkipLog

### 2.1 为什么不用两个裸参数

需要记录两类跳过：同名跳过（R1）与过滤规则跳过（R3）。若各加一个
`MutableList<String>` 参数，就要往 `uploadFolder` → `uploadDirectory` → `uploadFile`
三层各穿两个参数，签名迅速膨胀。

改用一个小holder，只穿一个参数：

```kotlin
/**
 * 本轮上传中被「静默跳过」的文件登记处。
 * 两个列表都必须是线程安全的 —— 文件夹路径下 uploadFile 并发执行（Semaphore(3)）。
 */
private class SkipLog {
    val sameName: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val filtered: MutableList<String> = Collections.synchronizedList(mutableListOf())

    fun isEmpty(): Boolean = sameName.isEmpty() && filtered.isEmpty()

    /** 汇总成警告文案；无跳过时返回空列表。 */
    fun toWarnings(): List<String> = buildList {
        if (filtered.isNotEmpty()) {
            add("${filtered.size} 个文件未匹配过滤规则，已跳过")
        }
        if (sameName.isNotEmpty()) {
            add("跳过 ${sameName.size} 个同名文件：${preview(sameName)}")
        }
    }

    private fun preview(names: List<String>): String {
        val head = names.take(MAX_PREVIEW).joinToString("、")
        return if (names.size > MAX_PREVIEW) "$head …" else head
    }

    private companion object { const val MAX_PREVIEW = 10 }
}
```

### 2.2 与既有 `warnings` / `fileWarnings` 的关键区别

`warnings` 在文件夹路径下是**每文件一个** `fileWarnings`，最后 merge（`UploadExecutor.kt:307`、
`:339-341`）。`SkipLog` 相反 —— **全局唯一一份**，在一个上传任务内跨所有文件共享，
因为最终只输出一句汇总。

因此 `SkipLog` 在 `execute()`（`:131` 附近）创建一次，向下传引用，不做 merge。

### 2.3 收集点

`uploadFile()` 内两处 `return false` 之前：

```kotlin
// filterRegex 分支（现 :381-384）
if (filterRegex != null && !filterRegex.containsMatchIn(localName)) {
    skips.filtered.add(localName)
    progressTracker.markComplete(fileSize)
    return false
}

// 同名跳过分支（现 :394-397）
if (plan.skip) {
    skips.sameName.add(localName)
    progressTracker.markComplete(fileSize)
    return false
}
```

`uploadFile` 签名新增 `skips: SkipLog`（与 `warnings` 并列）。
调用点三处补传：`execute()` 单文件分支（:142）、`uploadFolder()`（经 `uploadDirectory`，
:315 附近）。

### 2.4 汇总输出点

**两处，各一次**：

- `execute()` 的单文件分支结束前 —— 在 `finally { updateTitle(...) }` 之后、
  `formatUploadWarnings(warnings)` 之前：`warnings.addAll(skips.toWarnings())`
- `uploadFolder()` 末尾 —— 与既有 `skippedFolders` 汇总（`:210`、`:230`）并列

**为什么不在 `uploadFile` 内部输出**：文件夹路径下每个文件一份 `fileWarnings`，
在其中输出会得到 N 条重复汇总。

---

## 3. 文案格式

```
跳过 25 个同名文件：a.jpg、b.jpg、c.jpg、d.jpg、e.jpg、f.jpg、g.jpg、h.jpg、i.jpg、j.jpg …
3 个文件未匹配过滤规则，已跳过
```

**截断规则**：超过 10 个时取前 10 个 + ` …`。计数已写在句首，尾部不再重复「等 N 个」
（避免「跳过 25 个…等 25 个」的冗余）。

**为何必须汇总**：`formatUploadWarnings()`（`:89-99`）用「；」拼接所有警告。
若逐文件一条，文件夹里跳过上百个文件会拼成数百字的一段文本，既撑破 UI，
也让真正重要的警告被淹没。

---

## 4. 展示端收尾

`formatUploadWarnings` 对非「本地」开头的警告做 `distinct()` 后原样输出 ——
本任务新增的文案不以「本地」开头，不会被误并入「N 个本地项目未能删除」那类汇总。

`TasksScreen.kt:361-364` 给列表卡片上的警告 `Text` 加 `maxLines = 2` +
`overflow = TextOverflow.Ellipsis`：警告文本变长后，不加限制会撑破卡片高度。
详情页（`TaskDetailScreen.kt:244`）保持完整展示，不加限制。

---

## 5. 兼容性与回滚

- **无数据库变更**，无迁移。`TaskEntity.errorMessage` 字段复用。
- **无对外契约变更**。`execute()` 返回值类型不变。
- 已存在的历史任务不受影响（其 `errorMessage` 保持原值）。
- 回滚：改动集中在单个 service 文件与一处 UI 属性，`git revert` 即可整体回退。

---

## 6. 已知局限（用户已知悉并接受）

「远端大小 == 本地大小」仍被当作「内容相同」的判据。大小相等但内容不同的文件
**仍会被跳过且现在会显示为「跳过同名文件」**。升级为 `大小 + Last-Modified` 比对
已明确排除在本次之外（`prd.md` Out of Scope）。

本任务的价值在于：把静默行为变为可见行为 —— 用户至少能**发现**被跳过，而不是以为传完了。
