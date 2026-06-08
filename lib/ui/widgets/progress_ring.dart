import 'package:flutter/material.dart';

class ProgressRing extends StatelessWidget {
  const ProgressRing({
    super.key,
    required this.value,
    this.size = 36,
    this.strokeWidth = 3,
  });

  /// 0.0 - 1.0；为 null 表示 indeterminate。
  final double? value;
  final double size;
  final double strokeWidth;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CircularProgressIndicator(
            value: value,
            strokeWidth: strokeWidth,
            backgroundColor: theme.colorScheme.surfaceContainerHighest,
            valueColor: AlwaysStoppedAnimation(theme.colorScheme.primary),
          ),
          if (value != null)
            Text(
              '${(value!.clamp(0, 1) * 100).round()}%',
              style: theme.textTheme.labelSmall,
            ),
        ],
      ),
    );
  }
}
