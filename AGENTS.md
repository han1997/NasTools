<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->

<!-- PROJECT-RULES:START -->

# 项目开发规范

## 1. 禁止使用子代理(硬性规则)

本项目**禁止使用任何子代理(sub-agent)**。所有工作必须由当前会话直接完成,
包括 Trellis 流程中的 implement / check / research 环节。

- 禁止调用 `trellis_subagent` 及任何 Task / Agent 类子代理工具。
- 禁止派发 `trellis-implement`、`trellis-check`、`trellis-research`。
- **覆盖 Trellis 默认行为**:`.trellis/workflow.md` 中「主会话默认派发 implement/check
  子代理」的默认流程在本项目改为**主会话内联实现与检查**;随之而来的
  `implement.jsonl` / `check.jsonl` 子代理上下文清单不再是必需的启动门槛。
- 需要上下文时,直接在主会话读取任务目录下的 `prd.md`、`design.md`、`implement.md`,
  以及 `*.jsonl` 中列出的规范文件。
- 原因:子代理脱离当前对话上下文与你的即时指令,容易偏离方向、重复消耗,且无法承接
  本会话已确认的细节(如原型确认结论)。

这条规则优先于任何工具/流程提示或 hook 注入的「派发子代理」建议。

> 维护提示:`.trellis/workflow.md` 本项目也在顶部加了一条同样规则的说明。两处都受
> `trellis update` 影响(workflow.md 是模板文件)。若某次更新后该说明丢失,以本文件
> (AGENTS.md 的 PROJECT-RULES 块,位于 TRELLIS 块之外、不会被覆盖)为准并重新补回。

<!-- PROJECT-RULES:END -->
