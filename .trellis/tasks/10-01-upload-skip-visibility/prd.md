# 同名文件跳过可见化

父任务：`10-01-experience-feature-optimization`

---

## Goal

上传时因「远端已存在同名文件」而被跳过的文件，目前**不留任何用户可见记录**。
本任务让每次跳过都出现在任务的警告信息里，使用户能确认「哪些文件没传、为什么」。

**不改变默认冲突策略** —— 维持 `resume_or_overwrite`（「续传，完整则跳过」）。
改默认会破坏「重复上传同一文件走续传」这个主用例，远端还会不断累积副本。

## Background

### 现状查证（用户最初的疑问是「同名文件现在直接跳过？」）

默认策略并非无脑跳过。`UploadExecutor.prepareFileTarget`（`:509-542`）按远端大小分派：

| 远端 size vs 本地 | 行为 | 代码位置 |
|---|---|---|
| 相等 | **跳过** | `UploadExecutor.kt:535` |
| > 0 且 < 本地 | 从 `remoteSize` 续传 | `:536` |
| 其它 | 从头覆盖 | `:537` |

用户可选策略共五种（`UploadExecutor.kt:526-531`，标签见 `BrowserScreen.kt:630-634`、
`PresetEditScreen.kt:302-306`）：`resume_or_overwrite` / `overwrite` / `skip_existing` /
`rename` / `fail`。

### 缺陷

```kotlin
// UploadExecutor.kt:394-397
val plan = prepareFileTarget(adapter, remotePath, fileSize, options)
if (plan.skip) {
    progressTracker.markComplete(fileSize)   // 计入「已完成」字节
    return false                             // 不留任何记录
}
```

文件被算作已完成，却从未上传；任务详情里查不到痕迹。对比：**文件夹**跳过是有提示的
（`UploadExecutor.kt:210`、`:230` 添加「跳过 N 个已存在的文件夹」）。

**同类第二处**：`filterRegex` 不匹配的文件（`:381-384`）同样走
`markComplete + return false`，也是静默的。

### 可复用的既有机制

- `warnings: MutableList<String>`：`:131` 创建，贯穿单文件与文件夹两条路径，
  由 `formatUploadWarnings()`（`:89-99`）汇总成字符串。
- `skippedFolders: MutableList<String>`：**完全对口的先例** ——
  `:187` 创建 → `:286` 收集 → `:210`/`:230` 汇总成一句警告。本任务照此加 `skippedFiles`。
- 落库：`TaskManager.kt:76-77` 经 `taskDao.updateStatusWithError(id, "completed", warning, …)`
  写入 `TaskEntity.errorMessage`。
- 展示端**无需改动**：`TaskDetailScreen.kt:244` 在状态非 `failed` 时把该字段渲染为
  「警告信息」；`TasksScreen.kt:361-364` 在列表卡片上显示。

---

## Requirements

### R1 · 记录被跳过的文件

`uploadFile()` 增加 `skippedFiles: MutableList<String>` 参数（与既有 `warnings` 并列），
在跳过分支 `return false` 之前记录 `localName`。该分支同时服务 `skip_existing` 与默认模式的
「大小相等」去重，两者都要记。

文件夹路径下需线程安全，复用既有写法（参照 `:307` 的 `Collections.synchronizedList`）。

### R2 · 汇总为一句，不逐文件一条

在 `execute()` 的单文件分支与 `uploadFolder()` 末尾各输出一句：

```
跳过 N 个同名文件：a.jpg、b.jpg、…（超过 10 个时追加「等 N 个」）
```

**必须汇总**：`formatUploadWarnings` 用「；」拼接，文件夹跳过上百个文件会拼成一面文字墙。

### R3 · `filterRegex` 静默同样修掉

不匹配过滤规则的文件按同一通道记录，汇总为「N 个文件未匹配过滤规则，已跳过」。
与 R1/R2 属同一类缺陷，一并处理。

### R4 · 长警告的展示收尾

警告文本变长后，列表卡片上的 `Text(task.errorMessage, …)`
（`TasksScreen.kt:361-364`）需加 `maxLines = 2, overflow = TextOverflow.Ellipsis`，
避免撑破卡片。详情页是完整展示，不加限制。

---

## Acceptance Criteria

- [ ] AC1 文件夹上传中，远端已存在且大小相同的文件被跳过时，任务详情「警告信息」区
      显示「跳过 N 个同名文件：…」，且 N 与实际跳过数一致。
- [ ] AC2 单文件上传遇到同名同大小文件，同样可见。
- [ ] AC3 跳过数 > 10 时截断为前 10 个文件名 + ` …`，字符串长度有上界。
      （措辞以 `design.md` §3 为准 —— 句首已有计数，尾部不再重复「等 N 个」，
      否则会变成「跳过 25 个…等 25 个」的冗余。）
- [ ] AC4 未发生跳过的正常上传**不产生**任何新增警告（回归防线）。
- [ ] AC5 `filterRegex` 过滤掉的文件可见。
- [ ] AC6 列表卡片上的长警告截断为 2 行，不溢出。
- [ ] AC7 `formatUploadWarnings()` 有单元测试：覆盖多次跳过的汇总串、超 10 个的截断、
      空列表返回 `null`、以及不与「本地…未能删除」类警告互相吞并。
- [ ] AC8 编译通过，且 debug 与 release 包均能产出。

---

## Out of Scope

- **不改默认冲突策略**（维持 `resume_or_overwrite`）。
- **不引入「大小 + Last-Modified」的更严判据** —— 用户已选择本轮只解决可见性。
  缺点已知：大小相等但内容不同的文件仍会被跳过。
- **不改 `NasConfigEntity.password` 的明文存储** —— 归子任务
  `10-01-credential-encryption`。
- 不改任务列表的其它展示逻辑（排序/筛选归子任务 `10-01-list-sort-filter-search`）。

## Open Questions

无。
