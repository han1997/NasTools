# 凭据加密

父任务：`10-01-experience-feature-optimization`　·　依赖：无

## Goal

NAS 密码目前**明文存储在 Room 数据库**中，且列名 `passwordEncrypted` 误导读者。
本任务改为使用 Android Keystore 派生的密钥加密存储，并把已有明文迁移为密文。

## Background

```kotlin
// data/database/entity/NasConfigEntity.kt
@ColumnInfo(name = "passwordEncrypted")
val password: String,          // ← 实际是明文
```

全仓**无任何加密实现**：grep `EncryptedSharedPreferences` / `MasterKey` / `Cipher` /
`Encrypt` 在 `app/src/main/java` 下零命中。

数据库版本与迁移机制：`data/database/AppDatabase.kt`、`data/database/DatabaseMigrations.kt`
（规划阶段需核实当前 version 与迁移写法）。

## Requirements（待规划阶段细化）

- 用 Android Keystore 生成 / 获取 AES 密钥，加密 `password` 后入库。
- 数据库迁移：把现存明文密码加密回填；迁移必须幂等且失败可回滚。
- 读取路径解密；任何对外暴露 `NasConfigEntity` 的地方不得泄漏密文给 UI。
- 列名与实际语义对齐（保留 `passwordEncrypted` 或改名 —— 规划阶段定，
  改名需额外迁移）。

## Acceptance Criteria（待细化）

- [ ] 新建 NAS 配置后，直接查数据库可见 `password` 列为密文，非明文。
- [ ] 升级安装（带旧明文数据）后，连接测试仍能成功 —— 即迁移正确。
- [ ] 加密往返有单元测试或可复现的验证步骤。
- [ ] Keystore 密钥不可导出、不落盘为明文。

## Out of Scope

- 用户名加密（用户名的敏感度远低于密码，是否一并处理待定）。
- 其它表（`tasks` / `upload_presets`）的加密。

## Open Questions

- **迁移失败如何处置？** 若解密/加密中途失败，是回滚整个迁移还是保留明文并报警？
  这决定数据丢失风险，**规划阶段必须明确**。
- 是否引入 `androidx.security:security-crypto`（已废弃但可用）还是手写 Keystore 封装？
