# 界面美化

父任务：`10-01-experience-feature-optimization`　·　依赖：**必须在子任务 2、3 之后**

## Goal

在功能稳定之后做一轮视觉打磨：层级、留白、动效、细节一致性。

用户已定调「先修功能，再美」，故本任务排在最后。

## Background

前置基础已就绪（2026-10-01 的设计 token 迁移）：

- `presentation/theme/` 提供 `NasSpacing` / `NasShape` / `NasElevation` / `NasStatusColors`，
  已接入 9 个页面。
- `presentation/components/AppChrome.kt` 提供 `NasScaffold` / `NasTopAppBar` /
  `NasStatusBadge` / `NasMiniFab` / `NasIconContainer` / `NasEmptyState` 等共享组件。
- 已有 14 个 `@Preview`（6 页面 × 明暗 + 状态徽章六态）可用于快速比对。
- 深色模式已跟随系统。

## Requirements（待规划阶段细化）

**视觉方向需先与用户确认，不能自行决定。** 候选维度：

- 层级与留白：卡片、分组、分隔的层次关系是否清晰。
- 动效：既有 `NasMotion`（Quick/Standard/Emphasis）是否被一致使用，
  页面切换与列表进出是否有过渡。
- 细节一致性：图标粗细、圆角、字重、状态色使用是否统一。
- 深色模式下的对比度与层次（深色 UI 上线不久，值得专项过一遍）。

## Acceptance Criteria（待细化）

- [ ] 视觉方向经用户确认后写入 `design.md`，不自作主张。
- [ ] 复用现有 token 与共享组件，**不新造平行体系**。
- [ ] 明 / 暗两套均经用户目视验收。
- [ ] 不引入功能性回归（前置子任务 2、3 的验收项仍成立）。

## Out of Scope

- 状态覆盖与反馈正确性（子任务 2）。
- 布局结构调整到需要改导航的程度。

## Open Questions

- **视觉方向是什么？** 是否已有参考应用 / 风格偏好（如更紧凑、更圆润、
  更强的品牌色）。**启动前必须与用户确认。**
