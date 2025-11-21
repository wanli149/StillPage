# Help 模块

辅助工具模块，提供各种功能支持类。

## 功能说明

该模块包含了应用程序中各种辅助功能的实现，如书源帮助、配置管理、存储管理等。

## 目录结构

```
help/
├── book/                    # 书籍处理相关辅助类
├── config/                  # 配置管理
├── storage/                 # 存储管理
├── coroutine/               # 协程工具
├── http/                    # 网络请求工具
├── rhino/                   # Rhino 引擎辅助类
├── source/                  # 书源管理辅助类
├── AppFreezeMonitor.kt     # 应用冻结监控
├── AppWebDav.kt            # WebDAV 同步
├── Backup.kt               # 备份功能
├── CrashHandler.kt         # 崩溃处理
├── DefaultData.kt          # 默认数据
├── LauncherIconHelp.kt     # 启动器图标帮助
├── LifecycleHelp.kt        # 生命周期帮助
├── RuleBigDataHelp.kt      # 规则大数据帮助
└── README.md               # 本文件
```

## 核心功能类

### 书籍处理 (book/)
- `BookHelp` - 书籍处理辅助类
- `ContentProcessor` - 内容处理器
- `ChapterProvider` - 章节内容提供者

### 配置管理 (config/)
- `AppConfig` - 应用配置管理
- `ReadBookConfig` - 阅读配置管理
- `ThemeConfig` - 主题配置管理

### 存储管理 (storage/)
- `Backup` - 数据备份功能
- `AppWebDav` - WebDAV 同步支持

### 网络请求 (http/)
- `OkHttp` 客户端配置
- `Cronet` 网络库集成

### 书源管理 (source/)
- `SourceHelp` - 书源管理辅助类
- `AnalyzeRule` - 规则解析器

## 使用方法

```kotlin
// 使用配置管理
val config = AppConfig

// 使用书籍处理辅助类
BookHelp.saveContent(book, chapter, content)

// 使用网络请求工具
val response = okHttpClient.newCall(request).execute()
```

## 注意事项

1. 辅助类应保持功能单一，避免过于复杂
2. 工具类方法应尽量保持静态或使用单例模式
3. 涉及网络请求的功能应处理异常情况
4. 数据处理类应考虑性能和内存使用