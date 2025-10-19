# Service 模块

后台服务模块，包含应用程序的各种后台服务实现。

## 功能说明

该模块实现了应用程序中需要在后台运行的各种服务，如下载服务、朗读服务、同步服务等。

## 目录结构

```
service/
├── DownloadService.kt      # 下载服务
├── ReadAloudService.kt     # 朗读服务
├── WebService.kt           # Web 服务
├── UpdateService.kt        # 更新服务
├── BackupService.kt        # 备份服务
├── SyncService.kt          # 同步服务
├── CacheService.kt         # 缓存服务
├── CheckSourceService.kt   # 书源检查服务
├── CoroutineService.kt     # 协程服务
├── MediaService.kt         # 媒体服务
└── README.md              # 本文件
```

## 核心服务

### 下载服务 (DownloadService)
负责书籍章节内容的后台下载：
- 支持断点续传
- 多任务并发下载
- 下载进度通知
- 下载完成回调

### 朗读服务 (ReadAloudService)
提供文本到语音的朗读功能：
- 支持多种 TTS 引擎
- 朗读进度控制
- 语音参数调节
- 后台持续朗读

### Web 服务 (WebService)
管理 Web 服务器的启动和停止：
- HTTP 服务管理
- WebSocket 服务管理
- 服务状态监控
- 端口冲突处理

### 更新服务 (UpdateService)
处理应用程序和书源的自动更新：
- 检查新版本
- 下载更新包
- 应用更新
- 更新日志显示

### 备份服务 (BackupService)
管理数据的备份和恢复：
- 本地备份
- WebDAV 同步
- 自动备份策略
- 备份文件管理

### 同步服务 (SyncService)
处理多设备间的数据同步：
- 阅读进度同步
- 书签同步
- 书源同步
- 冲突解决

## 服务生命周期

所有服务都遵循 Android Service 生命周期：
- onCreate() - 服务创建
- onStartCommand() - 服务启动
- onBind() - 服务绑定
- onDestroy() - 服务销毁

## 使用方法

```kotlin
// 启动下载服务
val intent = Intent(context, DownloadService::class.java)
intent.putExtra("bookUrl", bookUrl)
context.startService(intent)

// 绑定朗读服务
val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        // 服务连接成功
    }
    
    override fun onServiceDisconnected(name: ComponentName) {
        // 服务断开连接
    }
}
context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
```

## 注意事项

1. 服务应在独立线程中执行耗时操作
2. 及时释放资源，避免内存泄漏
3. 处理服务异常情况，确保服务稳定性
4. 合理控制服务并发数量
5. 服务间通信应使用标准机制（如广播、事件总线）
6. 注意电池优化对后台服务的影响