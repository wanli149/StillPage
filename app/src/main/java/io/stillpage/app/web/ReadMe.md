# Web 模块

Web 服务模块，提供 HTTP API 和 WebSocket 服务。

## 功能说明

该模块实现了 Web 服务功能，为外部应用和 Web 前端提供数据接口。

## 目录结构

```
web/
├── HttpServer.kt           # HTTP 服务器实现
├── WebSocketServer.kt      # WebSocket 服务器实现
├── socket/                 # WebSocket 处理器
├── utils/                  # Web 工具类
└── README.md              # 本文件
```

## 核心组件

### HTTP 服务器
- `HttpServer` - 基于 NanoHTTPD 的 HTTP 服务器
- 处理 REST API 请求
- 提供静态资源服务

### WebSocket 服务器
- `WebSocketServer` - WebSocket 服务器实现
- 实时通信支持
- 搜索和调试功能

### 控制器
- `BookController` - 书籍相关 API 控制器
- `BookSourceController` - 书源相关 API 控制器
- `RssSourceController` - RSS 源相关 API 控制器
- `ReplaceRuleController` - 替换规则相关 API 控制器

## API 接口

### 书籍相关
- `GET /getBookshelf` - 获取书架数据
- `GET /getChapterList` - 获取章节列表
- `GET /getBookContent` - 获取章节内容
- `POST /saveBook` - 保存书籍
- `POST /deleteBook` - 删除书籍

### 书源相关
- `GET /getBookSources` - 获取所有书源
- `GET /getBookSource` - 获取指定书源
- `POST /saveBookSource` - 保存书源
- `POST /saveBookSources` - 批量保存书源
- `POST /deleteBookSources` - 批量删除书源

### WebSocket 接口
- `/searchBook` - 搜索书籍
- `/bookSourceDebug` - 书源调试
- `/rssSourceDebug` - RSS 源调试

## 使用方法

```kotlin
// 启动 Web 服务
val httpServer = HttpServer(port)
httpServer.start()

// 处理 API 请求
val response = BookController.getBookshelf()
```

## 安全考虑

1. API 接口需要验证请求来源
2. 敏感操作需要身份验证
3. 数据传输应使用 HTTPS
4. 限制请求频率防止滥用
5. 输入数据需要验证和过滤

## 注意事项

1. Web 服务需要在主线程外启动
2. 大数据传输应使用流式处理
3. 及时关闭连接释放资源
4. 处理网络异常和超时情况
5. 日志记录便于问题排查