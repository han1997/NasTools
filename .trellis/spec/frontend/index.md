# Frontend Development Guidelines

> Best practices for frontend development in this project.

---

## Overview

本目录是 Jetpack Compose 前端开发规范入口。**所有 UI 开发必须先读 [原型优先契约](./prototype-first.md)**，对照 `prototypes/nastools-ui/BASELINE.md` 中的已确认基准；原型未覆盖的 UI 先补原型并经用户确认，不能直接实现。

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Prototype First](./prototype-first.md) | 强制原型先行、版本与确认门槛、功能保留、完整状态及视觉验收 | Filled |
| [Compose Optimization](./compose-optimization.md) | Performance optimization patterns for Jetpack Compose UI | Filled |
| [Design Tokens](./design-tokens.md) | 设计 token（间距/形状/高度/状态色）使用契约、dp 字面量三分法、预览规范 | Filled |
| [Navigation & Session](./navigation-session.md) | 四入口导航、FileTabSession 会话契约、未保存编辑保护模式 | Filled |
| [State Feedback](./state-feedback.md) | 页面加载/空态/错误态、Snackbar 一次性反馈、危险操作确认契约 | Filled |

---

## How to Fill These Guidelines

For each guideline file:

1. Document your project's **actual conventions** (not ideals)
2. Include **code examples** from your codebase
3. List **forbidden patterns** and why
4. Add **common mistakes** your team has made

The goal is to help AI assistants and new team members understand how YOUR project works.

---

**Language**: All documentation should be written in **Chinese** with code examples.
