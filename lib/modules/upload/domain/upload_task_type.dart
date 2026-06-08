/// Upload 模块的任务 type 字段常量。
///
/// 与 `task.moduleId == 'upload'` 配合使用，由 TaskManager 路由到对应
/// executor。设计上单独抽常量类（而不是 enum）以保持 schema 字符串友好性
/// —— 旧任务行的 type 是裸字符串，需要向前兼容。
class UploadTaskType {
  UploadTaskType._();

  /// 上传整个文件夹（递归 SAF tree URI），保留相对路径结构。
  static const folderUpload = 'folder_upload';

  /// 上传单个文件到指定远端目录。
  static const fileUpload = 'file_upload';
}
