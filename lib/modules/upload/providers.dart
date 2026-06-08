import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import 'executor/file_upload_task_executor.dart';
import 'executor/upload_task_executor.dart';
import 'service/local_source.dart';
import 'service/upload_service.dart';

/// SAF 本地源 —— Android 上的唯一实现。
final localSourceProvider = Provider<LocalSource>((ref) {
  return SafLocalSource();
});

final uploadServiceProvider = Provider<UploadService>((ref) {
  return UploadService(
    database: ref.watch(appDatabaseProvider),
    taskManager: ref.watch(taskManagerProvider),
    logger: ref.watch(appLoggerProvider),
    localSource: ref.watch(localSourceProvider),
  );
});

final uploadTaskExecutorProvider = Provider<UploadTaskExecutor>((ref) {
  return UploadTaskExecutor(
    database: ref.watch(appDatabaseProvider),
    logger: ref.watch(appLoggerProvider),
    adapterFactory: ref.watch(adapterFactoryProvider),
    uploadService: ref.watch(uploadServiceProvider),
  );
});

final fileUploadTaskExecutorProvider = Provider<FileUploadTaskExecutor>((ref) {
  return FileUploadTaskExecutor(
    database: ref.watch(appDatabaseProvider),
    logger: ref.watch(appLoggerProvider),
    adapterFactory: ref.watch(adapterFactoryProvider),
    uploadService: ref.watch(uploadServiceProvider),
  );
});
