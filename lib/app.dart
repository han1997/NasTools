import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'ui/router/app_router.dart';
import 'ui/theme/app_theme.dart';

class NasToolsApp extends ConsumerWidget {
  const NasToolsApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = buildAppRouter();
    return MaterialApp.router(
      title: 'NasTools',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      routerConfig: router,
    );
  }
}
