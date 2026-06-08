import 'package:flutter/services.dart';

/// Dart 端通过 MethodChannel 与 Kotlin 端交互的薄封装。
///
/// 两个 channel：
/// * `nastools/service` —— FGS 启停、通知更新、权限申请
/// * `nastools/saf` —— SAF 树 URI 与文件 IO（在 [SafLocalSource] 中独立使用）
class ServiceBridge {
  ServiceBridge._() : _ch = const MethodChannel('nastools/service');
  static final ServiceBridge instance = ServiceBridge._();

  final MethodChannel _ch;

  Future<void> startService() async {
    await _ch.invokeMethod<bool>('startService');
  }

  Future<void> stopService() async {
    await _ch.invokeMethod<bool>('stopService');
  }

  Future<void> updateNotification({
    required String title,
    required String message,
    int progress = -1,
    int max = 0,
  }) async {
    await _ch.invokeMethod<bool>('updateNotification', {
      'title': title,
      'message': message,
      'progress': progress,
      'max': max,
    });
  }

  /// 申请 POST_NOTIFICATIONS 权限。返回最终是否被授予 ——
  /// `true` 表示已授予 / 不需要（API < 33），`false` 表示用户拒绝。
  Future<bool> requestNotificationPermission() async {
    final r = await _ch.invokeMethod<bool>('requestNotificationPermission');
    return r ?? false;
  }

  Future<void> requestIgnoreBatteryOptimizations() async {
    await _ch.invokeMethod<bool>('requestIgnoreBatteryOptimizations');
  }

  Future<bool> isBatteryOptimizationIgnored() async {
    final r = await _ch.invokeMethod<bool>('isBatteryOptimizationIgnored');
    return r ?? false;
  }
}
