# NasTools - Android 原生版

个人 NAS 工具箱 - 基于 Jetpack Compose 的原生 Android 应用。

## 项目状态

✅ **已完成 Flutter → Compose 迁移** （核心架构 60%）

- ✅ Jetpack Compose UI
- ✅ Room 数据库
- ✅ OkHttp WebDAV 客户端
- ✅ Hilt 依赖注入
- ✅ MVVM 架构
- ⏳ 文件浏览器（待实现）
- ⏳ 上传功能（待实现）

**详细报告**：查看 `MIGRATION_REPORT.md`  
**快速开发**：查看 `QUICKSTART.md`

## 快速开始

### 前置要求

- JDK 17+
- Android Studio Hedgehog (2023.1.1) 或更高
- Android SDK 34

### 构建项目

1. **用 Android Studio 打开项目**
   ```
   File -> Open -> 选择此目录
   ```

2. **同步 Gradle**
   ```bash
   ./gradlew build
   ```

3. **运行应用**
   - 连接 Android 设备或启动模拟器
   - 点击 Run 按钮（或 Shift+F10）

### 编译问题修复

如遇到 Kapt 错误，在 `app/build.gradle.kts` 添加：

```kotlin
kapt {
    correctErrorTypes = true
    arguments {
        arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
    }
}
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9.22 | 语言 |
| Jetpack Compose | 1.6.0 | UI |
| Room | 2.6.1 | 数据库 |
| Hilt | 2.50 | 依赖注入 |
| OkHttp | 4.12.0 | 网络 |
| Coroutines | 1.7.3 | 异步 |
| Navigation Compose | 2.7.6 | 导航 |
| Material 3 | 1.2.0 | 设计系统 |

## 项目结构

```
app/src/main/java/com/nastools/app/
├── data/
│   ├── database/       # Room 数据库 + DAO
│   ├── network/        # WebDAV 客户端
│   └── repository/     # 数据仓库
├── domain/
│   └── model/          # 业务模型
├── presentation/       # Compose UI
│   ├── home/           # 首页
│   ├── tasks/          # 任务中心
│   ├── theme/          # Material 3 主题
│   └── navigation/     # 导航图
├── service/            # 任务管理器
├── di/                 # Hilt 模块
└── MainActivity.kt
```

## 功能清单

### 已实现 ✅
- [x] Material 3 主题（浅色/深色）
- [x] 首页：连接列表
- [x] 任务中心：3 Tab（活跃/完成/失败）
- [x] Room 数据库：配置/任务/预设/日志
- [x] WebDAV 客户端：CRUD 操作
- [x] 任务管理器：协程调度 + 状态机

### 待实现 ⏳
- [ ] 文件浏览器（列表/网格视图）
- [ ] 文件上传（断点续传）
- [ ] NAS 配置页面
- [ ] 设置页面
- [ ] 上传预设管理
- [ ] Foreground Service 集成

## 开发指南

参考 `QUICKSTART.md` 获取：
- 快速开发模板
- 调试技巧
- 常见问题解决

## 构建 APK

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease

# 产物位置
# app/build/outputs/apk/debug/app-debug.apk
```

## 协议

私有使用。

## 变更日志

完整迁移历史见 `CHANGELOG.md`。
