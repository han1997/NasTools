# 🔧 Android Studio 运行配置修复

## 错误信息
```
Error: Run configuration NasTools.app.main is not supported in the current project. 
Cannot obtain the package.
```

## 原因
Android Studio 没有正确识别项目为 Android 项目，或者 Gradle 同步失败。

## 解决方案

### 方案 1: 重新导入项目（推荐）

1. **关闭 Android Studio**

2. **删除 IDE 缓存**
   ```bash
   # 删除 .idea 目录
   rm -rf .idea
   
   # 删除 .gradle 目录（可选）
   rm -rf .gradle
   ```

3. **重新打开项目**
   - 打开 Android Studio
   - `File` → `Open`
   - 选择 `C:\Users\hanhu\Code\NasTools`
   - 等待 Gradle 同步完成

### 方案 2: 手动创建运行配置

1. **点击顶部工具栏的运行配置下拉框**
2. **选择 `Edit Configurations...`**
3. **点击左上角 `+` → `Android App`**
4. **配置如下**:
   - Name: `app`
   - Module: `NasTools.app.main`
   - Package: `com.nastools.app`
5. **点击 `OK`**

### 方案 3: 修复 Gradle 同步

1. **清理构建缓存**
   ```bash
   ./gradlew clean
   ```

2. **重新同步**
   - 点击 `File` → `Sync Project with Gradle Files`
   - 或点击工具栏的 🐘 图标

3. **修复 Kapt 错误**（如果同步失败）
   
   在 `app/build.gradle.kts` 添加：
   ```kotlin
   kapt {
       correctErrorTypes = true
       arguments {
           arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
       }
   }
   ```

### 方案 4: 验证项目结构

确认以下文件存在：

```
NasTools/
├── settings.gradle.kts       ← 必须
├── build.gradle.kts           ← 必须
├── app/
│   ├── build.gradle.kts       ← 必须
│   └── src/main/
│       ├── AndroidManifest.xml ← 必须
│       └── java/...
```

如果缺失，检查是否打开了错误的目录。

### 方案 5: 从命令行运行

如果 Android Studio 仍有问题，可以用命令行：

```bash
# 连接设备或启动模拟器
adb devices

# 安装并运行
./gradlew installDebug
adb shell am start -n com.nastools.app/.MainActivity
```

## 常见问题

### Q: Gradle 同步失败
**A**: 检查网络连接，确保可以访问 Maven 仓库（已配置阿里云镜像）

### Q: 找不到 Android SDK
**A**: 
1. 打开 `File` → `Project Structure`
2. 选择 `SDK Location`
3. 设置 Android SDK 路径

### Q: compileSdkVersion 34 not found
**A**: 在 Android Studio 中安装 Android SDK 34
- `Tools` → `SDK Manager`
- 勾选 `Android 14.0 (API 34)`
- 点击 `Apply`

## 验证修复

成功修复后，你应该看到：

1. ✅ Gradle 同步成功（底部状态栏显示绿色勾）
2. ✅ 工具栏出现运行配置下拉框（显示 "app"）
3. ✅ 绿色 Run 按钮可点击
4. ✅ 项目结构显示为 Android 项目（有 `app` 模块）

## 快速验证命令

```bash
# 验证项目结构
ls -la settings.gradle.kts app/build.gradle.kts

# 验证 Gradle 可用
./gradlew tasks

# 尝试构建
./gradlew assembleDebug
```

---

**如果以上方法都不行，请提供完整的错误日志。**
