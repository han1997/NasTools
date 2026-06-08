import 'dart:async';
import 'dart:collection';

/// 简易并发闸门 —— 用信号量限制同时运行的任务数。
class ConcurrencyGate {
  ConcurrencyGate(this.maxConcurrent) : assert(maxConcurrent > 0);

  int maxConcurrent;
  int _inUse = 0;
  final Queue<Completer<void>> _waiters = Queue();

  int get inUse => _inUse;
  int get available => maxConcurrent - _inUse;

  Future<void> acquire() {
    if (_inUse < maxConcurrent) {
      _inUse++;
      return Future<void>.value();
    }
    final c = Completer<void>();
    _waiters.add(c);
    return c.future;
  }

  void release() {
    if (_waiters.isEmpty) {
      _inUse = (_inUse - 1).clamp(0, maxConcurrent);
      return;
    }
    final next = _waiters.removeFirst();
    next.complete();
  }

  /// 在运行期间调整并发上限。新上限提升时唤醒等待者。
  void resize(int newMax) {
    assert(newMax > 0);
    maxConcurrent = newMax;
    while (_inUse < maxConcurrent && _waiters.isNotEmpty) {
      final w = _waiters.removeFirst();
      _inUse++;
      w.complete();
    }
  }
}
