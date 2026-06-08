import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../task/task_manager.dart';

/// 模块（"插件"）接口。
///
/// 模块 = 一个可独立装载的业务单元。它负责：
/// * 在 [registerTaskExecutors] 中给 TaskManager 注册自己的 executor
/// * 在 [registerRoutes] 中给路由器注册自己的页面
/// * 在 [registerProviders] 中暴露自己的 Riverpod overrides（可选）
abstract class Module {
  String get id;
  String get displayName;

  /// 应用启动时调用 —— 适合做一次性初始化。
  Future<void> onStart(ProviderContainer container) async {}

  /// 应用关闭时调用 —— 适合释放资源。
  Future<void> onDispose() async {}

  /// 注册任务执行器（如果该模块产生异步任务）。
  void registerTaskExecutors(TaskManager tm, ProviderContainer container) {}

  /// 注册路由（页面 / 深链）。
  List<RouteBase> routes() => const [];

  /// 给 Provider scope 提供 overrides（如果模块需要全局单例）。
  List<Override> providerOverrides() => const [];
}
