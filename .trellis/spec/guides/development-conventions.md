# 开发流程约定

本文记录本项目的硬性开发流程规则,**优先于 Trellis 默认流程**。违反这些规则等于交付不合格。

相关:[原型优先契约](../frontend/prototype-first.md)、[状态反馈](../frontend/state-feedback.md)。

---

## 1. 禁止使用子代理(硬性规则)

本项目**禁止使用任何子代理(sub-agent)**。所有工作——包括 Trellis 的 implement / check /
research 环节——必须由当前会话直接完成。

### 规则

- 禁止调用 `trellis_subagent` 工具,以及任何 Task / Agent 类子代理调用。
- 禁止派发 `trellis-implement`、`trellis-check`、`trellis-research`。
- 禁止以「子代理更专注/更省上下文」为理由绕过本条。

### 对 Trellis 默认流程的覆盖

- `.trellis/workflow.md` 与工具提示中「主会话默认派发 implement/check 子代理」的默认行为,
  在本项目改为:**主会话内联实现与检查**。
- 因此 `implement.jsonl` / `check.jsonl`(子代理上下文清单)**不再是启动门槛**;
  它们仍可作为「本任务要读哪些规范」的索引,但由主会话自己读取。
- 规划产物 `prd.md` / `design.md` / `implement.md` 仍然必需——它们约束的是主会话自身。

### 为什么

- 子代理脱离当前对话上下文,拿不到本会话刚确认的结论(例如原型确认、用户即时调整),
  容易按旧理解返工。
- 子代理与主会话重复读写同一批文件,增加冲突与消耗。
- 用户与本会话之间的即时指令需要直接生效,不能经一层转述。

### 错误与正确

| 错误 | 正确 |
|---|---|
| 用 `trellis_subagent` 派发实现/检查 | 主会话直接读规范、写代码、跑测试 |
| 因 `implement.jsonl` 为空而拒绝开始 | 主会话读 `prd.md`/`design.md`/`implement.md` 后开始 |
| 让子代理「顺便」改原型或提交 | 主会话按流程自己完成,保持上下文连续 |

---

## 2. 先原型,后代码

任何 UI 变更必须先落 `prototypes/nastools-ui/` 并经用户确认,再改 Compose 代码。
详见 [原型优先契约](../frontend/prototype-first.md)。即使方向已获口头确认,顺序也不能反。

---

## 3. 每轮交付必须双包

每轮交付必须同时构建 debug 与 release,并确认两个 APK 都产出:

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
ls app/build/outputs/apk/debug/ app/build/outputs/apk/release/
```

汇报「已构建」之前先看 `ls` 结果,避免只出 debug 包却声称双包。

---

## 变更记录

- 2026-10-11:依据用户要求建立本节第 1 条(禁止子代理),并固化第 2、3 条既有约定。