# Database Guidelines

> Database patterns and conventions for this project.

---

## Overview

<!--
Document your project's database conventions here.

Questions to answer:
- What ORM/query library do you use?
- How are migrations managed?
- What are the naming conventions for tables/columns?
- How do you handle transactions?
-->

(To be filled by the team)

---

## Query Patterns

<!-- How should queries be written? Batch operations? -->

(To be filled by the team)

---

## Migrations

### Scenario: Legacy Database File Preparation

#### 1. Scope / Trigger
- Trigger: Any change that copies, deletes, renames, or checkpoints SQLite database files before Room opens `AppDatabase`.
- Goal: Legacy database file migration must complete before Room opens the target database file.

#### 2. Signatures
```kotlin
// DatabaseModule.kt
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase

// DatabaseMigrations.kt
fun prepareDatabaseFiles(context: Context)
```

#### 3. Contracts
- `DatabaseMigrations.prepareDatabaseFiles(context)` must run before `Room.databaseBuilder(...).build()` returns the singleton database.
- `prepareDatabaseFiles` may inspect both legacy and target databases, checkpoint the legacy WAL, delete the empty target database, and copy `nastools.db`, `nastools.db-wal`, and `nastools.db-shm` to `nastools_room.db*`.
- Row-count probes that only check whether data exists should open SQLite with `SQLiteDatabase.OPEN_READONLY`.
- No file-level database copy/delete may run from `RoomDatabase.Callback.onOpen`, because Room has already opened the target file.

#### 4. Validation & Error Matrix
| Condition | Result |
|-----------|--------|
| Legacy database has user data and target database is empty | Checkpoint legacy database, delete empty target, copy legacy files before Room opens |
| Target database already has user data | Do not copy legacy files |
| Legacy database does not exist or has no user data | Do not copy files |
| File preparation throws | Log a warning and continue opening Room |
| Code attempts to delete/copy target database from `onOpen` | Reject; this risks modifying an open database |

#### 5. Good/Base/Bad Cases
- Good: `provideAppDatabase()` calls `prepareDatabaseFiles(context)` before `Room.databaseBuilder(...)`.
- Base: No legacy file exists; preparation returns without touching the target database.
- Bad: `RoomDatabase.Callback.onOpen()` launches a background job that calls `prepareDatabaseFiles(context)` after Room opened `nastools_room.db`.

#### 6. Tests Required
- Unit or instrumentation test: target empty + legacy populated -> target database receives copied rows before Room opens.
- Unit or instrumentation test: target populated + legacy populated -> target data remains unchanged.
- Assertion points: `prepareDatabaseFiles` is invoked before `Room.databaseBuilder`, read-only probes do not create or modify target files, and `onOpen` does not perform file replacement.

#### 7. Wrong vs Correct
Wrong:
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
    .addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            databaseScope.launch {
                DatabaseMigrations.prepareDatabaseFiles(context)
            }
        }
    })
    .build()
```

Correct:
```kotlin
DatabaseMigrations.prepareDatabaseFiles(context)
return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
    .addMigrations(DatabaseMigrations.migration1To2)
    .build()
```

---

## Naming Conventions

<!-- Table names, column names, index names -->

(To be filled by the team)

---

## Common Mistakes

<!-- Database-related mistakes your team has made -->

(To be filled by the team)
