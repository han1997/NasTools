import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../pages/browser/image_preview_page.dart';
import '../pages/browser/remote_browser_page.dart';
import '../pages/browser/remote_folder_picker_page.dart';
import '../pages/home/home_page.dart';
import '../pages/nas_config/list_page.dart';
import '../pages/nas_config/edit_page.dart';
import '../pages/presets/edit_page.dart';
import '../pages/presets/list_page.dart';
import '../pages/settings/settings_page.dart';
import '../pages/tasks/tasks_page.dart';
import '../pages/upload_launch/upload_launch_page.dart';

GoRouter buildAppRouter() {
  return GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const HomePage(),
      ),
      GoRoute(
        path: '/nas',
        builder: (context, state) => const NasConfigListPage(),
        routes: [
          GoRoute(
            path: 'new',
            builder: (context, state) => const NasConfigEditPage(configId: null),
          ),
          GoRoute(
            path: 'edit/:id',
            builder: (context, state) => NasConfigEditPage(
              configId: state.pathParameters['id'],
            ),
          ),
        ],
      ),
      GoRoute(
        path: '/browser/:configId',
        builder: (context, state) => RemoteBrowserPage(
          configId: state.pathParameters['configId']!,
          path: state.uri.queryParameters['path'] ?? '/',
        ),
      ),
      GoRoute(
        path: '/folder_picker/:configId',
        builder: (context, state) => RemoteFolderPickerPage(
          configId: state.pathParameters['configId']!,
          initialPath: state.uri.queryParameters['path'] ?? '/',
          title: state.uri.queryParameters['title'],
        ),
      ),
      GoRoute(
        path: '/preview/image',
        builder: (context, state) => ImagePreviewPage(
          localPath: state.uri.queryParameters['localPath']!,
          title: state.uri.queryParameters['title'],
        ),
      ),
      GoRoute(
        path: '/upload/launch',
        builder: (context, state) {
          final configId = state.uri.queryParameters['configId']!;
          final remoteRoot = state.uri.queryParameters['remoteRoot'] ?? '/';
          return UploadLaunchPage(
            configId: configId,
            remoteRoot: remoteRoot,
          );
        },
      ),
      GoRoute(
        path: '/tasks',
        builder: (context, state) => const TasksPage(),
      ),
      GoRoute(
        path: '/presets',
        builder: (context, state) => const PresetListPage(),
        routes: [
          GoRoute(
            path: 'new',
            builder: (context, state) => const PresetEditPage(presetId: null),
          ),
          GoRoute(
            path: 'edit/:id',
            builder: (context, state) => PresetEditPage(
              presetId: state.pathParameters['id'],
            ),
          ),
        ],
      ),
      GoRoute(
        path: '/settings',
        builder: (context, state) => const SettingsPage(),
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      appBar: AppBar(title: const Text('页面不存在')),
      body: Center(child: Text(state.error?.toString() ?? '未知错误')),
    ),
  );
}
