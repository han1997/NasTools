import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/plugin/module.dart';
import '../../core/task/task_manager.dart';
import 'executor/upload_task_executor.dart';
import 'providers.dart';

/// Upload 模块的入口。
class UploadModule extends Module {
  @override
  String get id => 'upload';

  @override
  String get displayName => '上传';

  @override
  void registerTaskExecutors(TaskManager tm, ProviderContainer container) {
    tm.registerExecutor(container.read(uploadTaskExecutorProvider));
    tm.registerExecutor(container.read(fileUploadTaskExecutorProvider));
  }

  @override
  List<RouteBase> routes() => const [];
}
