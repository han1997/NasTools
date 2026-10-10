# 列表排序 / 筛选 / 搜索

父任务：`10-01-experience-feature-optimization`　·　依赖：无

## Goal

给任务列表、远端文件浏览器、预设列表加排序 / 筛选 / 搜索能力。
现状是这几个列表只能从头翻到尾，数据一多就难以定位。

## Background

- Room 已接入 Paging：`TaskRepository.pagingByStatus()` 返回 `PagingData<TaskEntity>`。
- 任务列表已有 3 个 Tab（活跃 / 完成 / 失败）——
  见 `TasksViewModel.kt:30-32` 按状态分组。
- 远端浏览器的列表 / 网格视图切换已存在且跨 session 保留**（Flutter 版有此描述；
  Compose 版是否实现需在规划阶段核实）**。

## Requirements（待规划阶段细化）

- 任务列表：按创建时间 / 大小 / 名称排序；按模块类型筛选；按标题搜索。
- 远端浏览器：按名称 / 大小 / 修改时间排序；当前目录内搜索。
- 预设列表：按名称搜索。
- 排序偏好是否跨 session 保留需在规划阶段定（引入持久化层，参考子任务
  `10-01-credential-encryption` 的存储方案）。

## Acceptance Criteria（待细化）

- [ ] 三个列表各自可排序、可筛选或搜索，且空结果有明确空态。
- [ ] 搜索为本地过滤还是远端查询（远端需服务端支持）需明确说明。

## Out of Scope

- 下载任务管理、媒体预览等新增实体。

## Open Questions

- 远端浏览器的搜索要本地过滤已列举的目录，还是发 WebDAV 查询？
  后者需服务端支持，多数 WebDAV 实现不支持全文搜索 —— **规划阶段必须先定**。
