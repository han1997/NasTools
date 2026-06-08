import 'package:flutter/material.dart';

class StatusChip extends StatelessWidget {
  const StatusChip({super.key, required this.status});
  final String status;

  @override
  Widget build(BuildContext context) {
    final (label, color) = _stylize(status, Theme.of(context).colorScheme);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withOpacity(0.32)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontWeight: FontWeight.w600,
          fontSize: 12,
        ),
      ),
    );
  }

  (String, Color) _stylize(String status, ColorScheme scheme) {
    switch (status) {
      case 'waiting':
        return ('等待', scheme.secondary);
      case 'running':
        return ('运行中', scheme.primary);
      case 'paused':
        return ('已暂停', scheme.tertiary);
      case 'failed':
        return ('失败', scheme.error);
      case 'completed':
        return ('已完成', Colors.green);
      case 'cancelled':
        return ('已取消', scheme.outline);
      default:
        return (status, scheme.outline);
    }
  }
}
