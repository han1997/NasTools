import 'dart:io';

import 'package:flutter/material.dart';
import 'package:photo_view/photo_view.dart';

/// 图片全屏预览页（PhotoView，支持缩放/平移）。
///
/// 输入是已下载到本地的图片文件路径（[localPath]）；下载逻辑在
/// [PreviewLauncher] 完成，本页面只负责展示。
class ImagePreviewPage extends StatelessWidget {
  const ImagePreviewPage({super.key, required this.localPath, this.title});

  final String localPath;
  final String? title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black54,
        foregroundColor: Colors.white,
        title: Text(
          title ?? localPath.split('/').last,
          overflow: TextOverflow.ellipsis,
        ),
      ),
      body: PhotoView(
        imageProvider: FileImage(File(localPath)),
        backgroundDecoration: const BoxDecoration(color: Colors.black),
        minScale: PhotoViewComputedScale.contained,
        maxScale: PhotoViewComputedScale.covered * 4,
        loadingBuilder: (_, __) =>
            const Center(child: CircularProgressIndicator()),
        errorBuilder: (_, error, __) => Center(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              '图片无法显示：$error',
              style: const TextStyle(color: Colors.white70),
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  }
}
