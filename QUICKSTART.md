# 🔧 快速修复与继续开发指南

## 🚨 立即修复：Kapt 编译错误

### 步骤 1: 更新 build.gradle.kts

在 `android/app/build.gradle.kts` 的 `kapt` 块中添加：

```kotlin
kapt {
    correctErrorTypes = true
    arguments {
        arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
    }
}
```

### 步骤 2: 清理并重新构建

```bash
cd android
./gradlew clean
./gradlew build --stacktrace
```

如果仍有错误，运行：
```bash
./gradlew build --info | grep "error:"
```

### 步骤 3: 常见修复

#### 错误：找不到 Hilt 生成的类
**解决**: 确保所有 ViewModel 和 Repository 都有 `@Inject constructor`

#### 错误：Room 未生成 DAO 实现
**解决**: 运行 `./gradlew kspDebugKotlin` 单独生成

#### 错误：Missing binding
**解决**: 检查 `di/` 模块中的 `@Provides` 方法

---

## 📝 待实现功能清单

### 优先级 1：核心功能（必须）

#### 1.1 文件浏览器页面 
**文件**: `presentation/browser/BrowserScreen.kt`
**估时**: 4 小时
```kotlin
@Composable
fun BrowserScreen(configId: String, path: String = "/") {
    // TODO: 实现列表/网格切换
    // TODO: 调用 WebDavClient.list(path)
    // TODO: 文件操作菜单（长按）
}
```

#### 1.2 上传执行器
**文件**: `service/UploadExecutor.kt`
**估时**: 6 小时
```kotlin
class UploadExecutor @Inject constructor(
    private val adapter: StorageAdapter
) {
    suspend fun execute(task: TaskEntity, onProgress: (Long, Long) -> Unit) {
        // TODO: 解析 payloadJson
        // TODO: 遍历本地文件（DocumentFile）
        // TODO: 断点续传逻辑
    }
}
```

#### 1.3 NAS 配置页面
**文件**: `presentation/config/ConfigEditScreen.kt`
**估时**: 3 小时
```kotlin
@Composable
fun ConfigEditScreen(configId: String?) {
    // TODO: 表单输入（name, baseUrl, username, password）
    // TODO: 连接测试按钮
    // TODO: 保存到数据库
}
```

### 优先级 2：增强功能（推荐）

#### 2.1 设置页面
**文件**: `presentation/settings/SettingsScreen.kt`
**估时**: 2 小时

#### 2.2 Foreground Service 集成
**文件**: `service/NasForegroundService.kt`（重构现有）
**估时**: 3 小时

#### 2.3 上传预设页面
**文件**: `presentation/presets/PresetListScreen.kt`
**估时**: 2 小时

### 优先级 3：高级功能（可选）

- 图片预览（Coil + Zoomable）
- 文件下载
- 批量操作
- 搜索过滤

---

## 🎯 快速开发模板

### 创建新页面模板

1. **创建 Screen**:
```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(topBar = { TopAppBar(...) }) { padding ->
        // UI 实现
    }
}
```

2. **创建 ViewModel**:
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository
) : ViewModel() {
    val uiState: StateFlow<MyUiState> = repository.observeData()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyUiState())
}
```

3. **添加到导航**:
```kotlin
// NavGraph.kt
composable("my_route") {
    MyScreen(onBack = { navController.popBackStack() })
}
```

### 创建新 Repository 模板

```kotlin
@Singleton
class MyRepository @Inject constructor(
    private val dao: MyDao
) {
    fun observeAll(): Flow<List<MyEntity>> = dao.observeAll()
    suspend fun insert(item: MyEntity) = dao.insert(item)
}
```

---

## 🧪 测试建议

### 单元测试（可选）
```kotlin
@Test
fun `test task status transition`() {
    runTest {
        val task = TaskEntity(id = "1", status = "waiting", ...)
        taskDao.insert(task)
        taskDao.updateStatus("1", "running")
        val updated = taskDao.getById("1")
        assertEquals("running", updated?.status)
    }
}
```

### UI 测试（推荐）
```kotlin
@Test
fun `test home screen shows configs`() {
    composeTestRule.setContent {
        HomeScreen(configs = listOf(...))
    }
    composeTestRule.onNodeWithText("NasTools").assertExists()
}
```

---

## 📚 学习资源

- **Jetpack Compose**: https://developer.android.com/compose
- **Room 数据库**: https://developer.android.com/training/data-storage/room
- **Hilt 依赖注入**: https://developer.android.com/training/dependency-injection/hilt-android
- **OkHttp**: https://square.github.io/okhttp/
- **Kotlin Coroutines**: https://kotlinlang.org/docs/coroutines-guide.html

---

## 🐛 调试技巧

### 查看数据库内容
```bash
adb shell
run-as com.nastools.app
cd databases
sqlite3 nastools.db
.tables
SELECT * FROM nas_configs;
```

### 查看日志
```bash
adb logcat | grep "NasTools"
```

### 清除应用数据
```bash
adb shell pm clear com.nastools.app
```

---

## 📞 需要帮助？

1. **编译错误**: 检查 `build.gradle.kts` 依赖版本
2. **运行时崩溃**: 查看 `adb logcat` 完整堆栈
3. **Hilt 注入失败**: 确保 `@Inject` 和 `@Provides` 匹配
4. **Room 查询错误**: 检查 SQL 语法和表结构

---

**最后更新**: 2026-06-12  
**项目状态**: ✅ 架构完成，待实现业务逻辑
