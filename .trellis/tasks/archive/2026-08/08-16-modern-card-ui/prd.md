# UI 美化 - 现代卡片风格

## Goal

将整个 NasTools Android 应用的 UI 从当前 Material Design 3 风格升级为现代卡片风格，去除 MD3 的视觉特征（渐变背景、渐变边框、半透明表面、圆形图标容器），替换为干净、扁平、留白充足的现代设计。

## What I already know

### 当前 UI 特征（需要改变的）
- **渐变背景**: `NasScaffold` 使用 `Brush.verticalGradient` 作为背景
- **渐变边框**: `nasCardBorder()` 使用 `Brush.linearGradient` 多色渐变
- **半透明表面**: 卡片 containerColor 使用 `surface.copy(alpha = 0.88f)`
- **小圆角**: `NasCardShape = RoundedCornerShape(8.dp)`，所有 shape 都在 4-8dp
- **圆形图标容器**: `NasIconContainer` 使用 `CircleShape` + border + shadowElevation
- **TopAppBar 渐变背景**: `NasTopAppBar` 使用渐变背景
- **状态徽章**: `NasStatusBadge` 使用 CircleShape + border + shadow
- **FAB 高阴影**: `NasMiniFab` 使用 8-12dp 阴影

### 项目结构
- Jetpack Compose + Material 3 (M3 API 不可避免，但视觉风格可以脱离 MD3)
- 主题文件: `theme/Color.kt`, `theme/Theme.kt`, `theme/Type.kt`
- 组件文件: `components/AppChrome.kt`, `components/NasMotion.kt`
- 8 个屏幕: Home, Browser, Tasks, TaskDetail, ConfigEdit, PresetList, PresetEdit, Settings, MediaViewer

### 现代卡片风格特征（目标）
- 大圆角 (16-20dp)
- 纯色背景，无渐变
- 无边框或极淡边框 (1dp outlineVariant)
- 柔和阴影 (2-4dp)
- 充足留白
- 简洁的图标容器（圆角矩形而非圆形）
- 干净的 TopAppBar（纯色或透明，无渐变）

## Assumptions (temporary)

- 保留 Material 3 API 作为底层（Card, Scaffold, TopAppBar 等），只改视觉表现
- 保留现有的 Motion 系统（动画时长/缓动）
- 保留现有的颜色体系（绿/棕/紫），但可能微调饱和度
- 不改变任何功能逻辑，只改 UI 外观

## Open Questions

(已全部解决)

## Requirements

- 去除所有渐变背景（NasScaffold, NasTopAppBar）
- 去除所有渐变边框，卡片无边框
- 卡片圆角：大卡片 16dp，小元素 12dp
- 卡片背景使用纯色 surface（不透明）
- 图标容器改为圆角矩形 (12dp)，无 border 无 shadow，仅 tinted background
- 简化状态徽章（去除 border + shadow）
- 降低 FAB 阴影
- 增大留白和间距
- 统一所有屏幕的视觉风格

## Acceptance Criteria

- [ ] 所有渐变背景已移除
- [ ] 所有渐变边框已移除
- [ ] 卡片圆角：大卡片 16dp，小元素 12dp
- [ ] 卡片无边框
- [ ] 图标容器为圆角矩形 (12dp)，无 border 无 shadow
- [ ] 状态徽章简化（无 border + shadow）
- [ ] TopAppBar 使用纯色/透明背景
- [ ] 所有 9 个屏幕视觉风格统一
- [ ] 编译通过，无功能回归

## Definition of Done

- 编译通过 (./gradlew assembleDebug)
- 无功能逻辑改动
- 所有屏幕视觉风格统一

## Out of Scope

- 功能逻辑改动
- 新增页面或组件
- 颜色体系大改（保留现有绿/棕/紫基调）
- 动画系统改动（保留 NasMotion）

## Technical Notes

- 核心改动文件: `AppChrome.kt`（组件层），`Theme.kt`（Shapes），`Color.kt`（可能微调）
- 屏幕文件改动较小，因为它们都通过 `nasCardColors()` / `nasCardBorder()` / `NasCardShape` 等间接引用样式
- 改动集中在组件层，屏幕层主要是间距调整
