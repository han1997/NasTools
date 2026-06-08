import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../task/task_manager.dart';
import 'module.dart';

/// 模块注册中心。
///
/// App 启动时把所有 [Module] 实例放进 registry，启动后统一调用
/// [registerAll] 把它们的 executor / route / provider 绑定到运行时。
class ModuleRegistry {
  ModuleRegistry(this._modules);

  final List<Module> _modules;
  List<Module> get all => List.unmodifiable(_modules);

  Future<void> initAll(ProviderContainer container, TaskManager tm) async {
    for (final m in _modules) {
      await m.onStart(container);
      m.registerTaskExecutors(tm, container);
    }
  }

  Future<void> disposeAll() async {
    for (final m in _modules.reversed) {
      await m.onDispose();
    }
  }
}
