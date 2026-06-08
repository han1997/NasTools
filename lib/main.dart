import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/background/notification_controller.dart';
import 'core/background/service_bridge.dart';
import 'core/plugin/module_registry.dart';
import 'core/providers.dart';
import 'modules/upload/upload_module.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final container = ProviderContainer();

  // 等待 SharedPreferences 就绪 —— SettingsRepository 依赖它。
  await container.read(sharedPreferencesProvider.future);

  // 初始化模块（注册 executors / routes / providers）。
  final registry = ModuleRegistry([UploadModule()]);
  final tm = container.read(taskManagerProvider);
  await registry.initAll(container, tm);

  // 启动调度循环（service isolate 启动后会接管；UI 端的 TM 仅用于本地调用控制）。
  await tm.start();

  // 启动 Android Foreground Service（如可用）。
  try {
    await ServiceBridge.instance.startService();
  } catch (_) {
    // 桌面 / 测试环境忽略。
  }

  // 把 Drift 活跃任务流桥接到 Android 通知。
  NotificationController(container.read(appDatabaseProvider)).start();

  runApp(UncontrolledProviderScope(
    container: container,
    child: const NasToolsApp(),
  ));
}
