# 图片 / 媒体预览

父任务：`10-01-experience-feature-optimization`　·　依赖：无

## Goal

点击远端图片 / 视频可直接预览，不必先下载到本地再用外部应用打开。

## Background

- `README.md`「待实现」清单列有「图片/媒体预览」。
- 远端浏览已具备：`presentation/browser/BrowserScreen.kt`，数据来自 `WebDavAdapter`。
- `StorageAdapter` 抽象（`data/network/StorageAdapter.kt`）已提供下载能力
  （`UploadExecutor.kt` 里有从远端读取的逻辑可参考），预览需要的是「取回字节到缓存」。

## Requirements（待规划阶段细化）

- 点击远端图片 → 全屏预览页，支持缩放 / 平移。
- 视频/音频 → 交给系统播放器（`Intent.ACTION_VIEW` + FileProvider），
  应用内不做播放器。
- 缓存：按「配置 + 远端路径」生成缓存键，命中则跳过下载；设置页提供清理入口。
- 下载中显示进度且可取消。

## Acceptance Criteria（待细化）

- [ ] 点击图片进入预览，二次打开同一图片走缓存不重复下载。
- [ ] 大文件有确认门槛（参考既有约定：> 50MB 确认、> 500MB 拒绝）。
- [ ] 缓存可清理，清理后不影响其它功能。
- [ ] FileProvider 配置正确，外部应用能打开缓存文件。

## Out of Scope

- 应用内视频播放器。
- 媒体库 / 相册聚合视图。

## Open Questions

- **缓存上限策略？** 按总字节数还是按条目数，超出后淘汰顺序如何定 ——
  不留上限会在手机上累积占用。规划阶段必须明确。
- 是否有 Glide / Coil 之类的图片加载依赖，还是手写？规划阶段核实现有依赖。
