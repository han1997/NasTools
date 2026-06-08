import 'dart:math';

/// 指数退避重试策略。
class RetryPolicy {
  const RetryPolicy({
    this.baseDelay = const Duration(seconds: 2),
    this.maxDelay = const Duration(minutes: 5),
    this.maxRetries = 5,
    this.jitter = 0.2,
  });

  final Duration baseDelay;
  final Duration maxDelay;
  final int maxRetries;

  /// 抖动比例（±jitter）。
  final double jitter;

  /// 计算第 [attempt] 次（从 1 开始）重试前的等待。
  ///
  /// 公式：min(base * 2^(attempt-1), max) × (1 ± jitter)
  Duration delayFor(int attempt) {
    final exp = (1 << (attempt - 1).clamp(0, 16)).toDouble();
    final raw = baseDelay * exp.toInt();
    final clamped = raw > maxDelay ? maxDelay : raw;

    final rand = Random();
    final factor = 1.0 + (rand.nextDouble() * 2 - 1) * jitter;
    final ms = (clamped.inMilliseconds * factor).round();
    return Duration(milliseconds: ms);
  }

  bool shouldRetry(int attempt) => attempt < maxRetries;
}
