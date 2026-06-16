# Error Handling

> How errors are handled in this project.

---

## Overview

This project is an Android NAS upload application. Error handling follows these principles:

1. **Fatal vs Non-Fatal**: Distinguish between errors that should fail the task vs warnings that allow completion
2. **Cross-Layer Propagation**: Errors flow from service layer → TaskManager → database with appropriate status
3. **User Communication**: Task status + errorMessage field carries both failures and completion warnings

---

## Error Severity Classification

### Fatal Errors (Task Status: `failed`)

Errors that prevent the primary operation from succeeding:
- Network failures during upload
- Configuration errors (missing/invalid NAS config)
- Authentication failures
- Remote path conflicts with `onConflict: "fail"`
- Storage access denied

**Behavior**: Throw `IOException`, caught by `TaskManager.executeTask()`, task marked as `failed` with retry capability.

### Non-Fatal Warnings (Task Status: `completed` + `errorMessage`)

Operations where the primary goal succeeded but cleanup/housekeeping failed:
- Local file/folder deletion failure after successful upload
- Non-blocking post-upload operations

**Behavior**: Return warning message from executor, `TaskManager` marks task as `completed` with `errorMessage` containing warning text.

---

## Error Handling Patterns

### Pattern 1: Fatal Error Propagation

**When**: Primary operation failure (upload, authentication, validation)

```kotlin
// Service Layer (UploadExecutor.kt)
suspend fun execute(task: TaskEntity, onProgress: ...): String? = withContext(Dispatchers.IO) {
    val config = configRepository.getById(configId) 
        ?: throw IOException("NAS 配置不存在") // Fatal: throw immediately
    
    // Network/upload errors propagate naturally as IOException
    adapter.upload(...)
}

// Orchestration Layer (TaskManager.kt)
try {
    val warning = uploadExecutor.execute(current) { ... }
    // Success path
} catch (e: Exception) {
    taskDao.updateStatusWithError(
        current.id, 
        "failed",           // Fatal error → failed status
        e.message ?: "上传失败", 
        retryCount + 1
    )
}
```

### Pattern 2: Non-Fatal Warning Collection

**When**: Primary operation succeeded, but cleanup/post-processing failed

```kotlin
// Service Layer - Warning accumulation
suspend fun execute(task: TaskEntity, onProgress: ...): String? = withContext(Dispatchers.IO) {
    val warnings = mutableListOf<String>()
    
    // Primary operation (throws on failure)
    uploadFolder(adapter, payload, options, totalBytes, warnings, onProgress)
    
    // Return aggregated warnings (null if none)
    return@withContext if (warnings.isEmpty()) null 
                       else "上传完成，${warnings.size} 个本地项目未能删除"
}

// Cleanup operations - Non-throwing
private fun deleteLocalSource(
    document: DocumentFile,
    warnings: MutableList<String>
): Boolean {
    return runCatching {
        if (!document.delete()) {
            warnings.add("无法删除: ${document.name}")
            return false
        }
        true
    }.getOrElse {
        warnings.add("删除失败: ${document.name} (${it.message})")
        false
    }
}

// Orchestration Layer - Conditional completion
val warning = uploadExecutor.execute(current) { ... }
if (warning != null) {
    taskDao.updateStatusWithError(current.id, "completed", warning, latest.retryCount)
} else {
    taskDao.updateStatus(current.id, "completed")
}
```

---

## Scenario: Post-Upload Deletion Warnings

### 1. Scope / Trigger
- **Trigger**: Cross-layer contract change for post-operation cleanup failures
- **Layers**: Service (UploadExecutor) → Orchestration (TaskManager) → Data (TaskDao)

### 2. Signatures

```kotlin
// UploadExecutor.kt
suspend fun execute(
    task: TaskEntity,
    onProgress: suspend (uploadedBytes: Long, totalBytes: Long) -> Unit
): String? // null = success, non-null = completed with warnings

private fun deleteLocalSource(
    document: DocumentFile,
    warnings: MutableList<String>
): Boolean // true = deleted, false = failed (added to warnings)

private suspend fun uploadDirectory(
    adapter: NasAdapter,
    directory: DocumentFile,
    remotePath: String,
    options: UploadPresetOptions,
    totalTaskBytes: Long,
    completedBytes: () -> Long,
    updateCompletedBytes: (Long) -> Unit,
    warnings: MutableList<String>,
    isRoot: Boolean = false, // true = don't delete self
    onProgress: suspend (Long, Long) -> Unit
)
```

```kotlin
// TaskManager.kt
private suspend fun executeTask(current: TaskEntity) {
    val warning: String? = when (current.moduleId) {
        "upload" -> uploadExecutor.execute(current) { ... }
        else -> throw IllegalArgumentException("未知模块")
    }
    
    if (warning != null) {
        taskDao.updateStatusWithError(current.id, "completed", warning, latest.retryCount)
    } else {
        taskDao.updateStatus(current.id, "completed")
    }
}
```

### 3. Contracts

**UploadExecutor.execute() Return Contract**:
- `null`: Upload succeeded, all cleanup succeeded (if enabled)
- `String`: Upload succeeded, cleanup partially failed, message contains warning summary

**Warning Message Format**:
- Template: `"上传完成，N 个本地项目未能删除"`
- `N` = count of failed deletion attempts (files/subdirectories, excluding root folder)

**Task Status Contract**:
- `"completed"` + no `errorMessage`: Perfect success
- `"completed"` + `errorMessage`: Success with non-fatal warnings
- `"failed"` + `errorMessage`: Fatal error, retryable

### 4. Validation & Error Matrix

| Condition | Result | Task Status | errorMessage |
|-----------|--------|-------------|--------------|
| Upload succeeds, no deletion requested | Success | `completed` | `null` |
| Upload succeeds, all deletions succeed | Success | `completed` | `null` |
| Upload succeeds, some deletions fail | Warning | `completed` | `"上传完成，N 个本地项目未能删除"` |
| Upload succeeds, root folder can't delete (SAF) | Success (by design) | `completed` | `null` |
| Upload fails (network, config, auth) | Fatal | `failed` | Error details |

### 5. Good/Base/Bad Cases

**Good (Perfect Success)**:
```kotlin
// Upload succeeds, delete-after-upload enabled, all files deleted
warnings = mutableListOf() // empty after execution
execute() returns null
TaskManager marks: status="completed", errorMessage=null
UI: Task in "已完成" tab with green checkmark
```

**Base (Success with Warnings)**:
```kotlin
// Upload succeeds, but 2 files can't be deleted (permission/in-use)
warnings = mutableListOf("无法删除: photo.jpg", "删除失败: video.mp4 (Permission denied)")
execute() returns "上传完成，2 个本地项目未能删除"
TaskManager marks: status="completed", errorMessage="上传完成，2 个本地项目未能删除"
UI: Task in "已完成" tab with amber warning indicator
```

**Bad (Fatal Error)**:
```kotlin
// Network failure during upload
execute() throws IOException("网络连接失败")
TaskManager catches, marks: status="failed", errorMessage="网络连接失败", retryCount++
UI: Task in "失败" tab, retryable
```

### 6. Tests Required

**Unit Tests** (`UploadExecutorTest`):
- `execute_uploadSucceeds_deletionDisabled_returnsNull()`
- `execute_uploadSucceeds_deletionSucceeds_returnsNull()`
- `execute_uploadSucceeds_someDeletionsFail_returnsWarning()`
- `execute_uploadFails_throwsException()`

**Assertion Points**:
```kotlin
// Success case
val result = executor.execute(task, onProgress)
assertNull(result)

// Warning case
val result = executor.execute(task, onProgress)
assertNotNull(result)
assertTrue(result!!.contains("2 个本地项目未能删除"))

// Failure case
assertThrows<IOException> {
    executor.execute(task, onProgress)
}
```

**Integration Tests** (`TaskManagerTest`):
- `executeTask_uploadSucceeds_deletionWarning_marksCompletedWithError()`
- `executeTask_uploadFails_marksFailedWithRetry()`

**Assertion Points**:
```kotlin
// Warning case
taskManager.executeTask(task)
val updated = taskDao.getById(task.id)
assertEquals("completed", updated?.status)
assertNotNull(updated?.errorMessage)
assertTrue(updated?.errorMessage!!.contains("上传完成"))

// Failure case
taskManager.executeTask(task)
val updated = taskDao.getById(task.id)
assertEquals("failed", updated?.status)
assertEquals(originalRetryCount + 1, updated?.retryCount)
```

### 7. Wrong vs Correct

#### Wrong (Before Fix)
```kotlin
// UploadExecutor - deletion failure treated as fatal
private fun deleteLocalSource(document: DocumentFile) {
    if (!document.delete()) {
        throw IOException("无法删除本地文件夹") // ❌ Makes entire task fail
    }
}

// TaskManager - no distinction between failure types
try {
    uploadExecutor.execute(current) { ... }
    taskDao.updateStatus(current.id, "completed")
} catch (e: Exception) {
    taskDao.updateStatusWithError(current.id, "failed", e.message, retryCount + 1)
}

// Result: User sees "上传失败" even though files uploaded successfully
```

#### Correct (After Fix)
```kotlin
// UploadExecutor - deletion failure collected as warning
private fun deleteLocalSource(
    document: DocumentFile,
    warnings: MutableList<String>
): Boolean {
    return runCatching {
        if (!document.delete()) {
            warnings.add("无法删除: ${document.name}") // ✅ Non-fatal
            return false
        }
        true
    }.getOrElse {
        warnings.add("删除失败: ${document.name} (${it.message})")
        false
    }
}

suspend fun execute(...): String? {
    val warnings = mutableListOf<String>()
    uploadFolder(..., warnings, ...) // ✅ Upload can succeed independently
    return if (warnings.isEmpty()) null else "上传完成，${warnings.size} 个本地项目未能删除"
}

// TaskManager - conditional completion
val warning = uploadExecutor.execute(current) { ... }
if (warning != null) {
    taskDao.updateStatusWithError(current.id, "completed", warning, latest.retryCount)
} else {
    taskDao.updateStatus(current.id, "completed")
}

// Result: User sees "已完成" with optional warning indicator
```

---

## Common Mistakes

### Mistake 1: Treating All Errors as Fatal

**Problem**: Post-operation cleanup failures (like file deletion) cause entire task to fail, even when primary operation succeeded.

**Symptom**: User sees "任务失败" even though files were uploaded successfully.

**Fix**: 
1. Classify errors by severity (fatal vs non-fatal)
2. Use warning collection pattern for non-fatal issues
3. Return warnings separately from exceptions

**Prevention**: Ask "If this operation fails, should the entire task fail?" If no, use warning collection.

### Mistake 2: Root Folder Deletion on SAF

**Problem**: Attempting to delete the folder selected via `ACTION_OPEN_DOCUMENT_TREE` fails because `ExternalStorageProvider` doesn't set `FLAG_SUPPORTS_DELETE` on tree roots.

**Symptom**: All folder uploads with "delete after upload" show warnings, even when contents are successfully deleted.

**Fix**: 
1. Add `isRoot` parameter to distinguish root from subdirectories
2. Root folders: skip self-deletion, only delete contents
3. Subdirectories: maintain "delete self after contents" behavior

**Prevention**: When working with SAF tree URIs, remember tree root has restricted permissions. Only attempt to delete children, not the root itself.
