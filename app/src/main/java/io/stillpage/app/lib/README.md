# Lib 模块

第三方库集成模块，包含对各种第三方库的封装和集成。

## 功能说明

该模块负责集成和封装应用程序中使用的第三方库，提供统一的接口和管理。

## 目录结构

```
lib/
├── cache/                  # 缓存库集成
├── database/               # 数据库库集成
├── network/                # 网络库集成
├── image/                  # 图片处理库集成
├── text/                   # 文本处理库集成
├── media/                  # 媒体库集成
├── utils/                  # 工具库集成
├── theme/                  # 主题库集成
├── dialog/                 # 对话框库集成
├── selector/               # 选择器库集成
├── permission/             # 权限库集成
└── README.md              # 本文件
```

## 集成的第三方库

### 网络库
- **OkHttp** - HTTP 客户端
- **Retrofit** - REST API 客户端
- **Cronet** - Google 网络库

### 图片处理库
- **Glide** - 图片加载和缓存
- **AndroidSVG** - SVG 图片支持

### 数据库库
- **Room** - SQLite 数据库抽象层

### 文本处理库
- **JSoup** - HTML 解析库
- **JsonPath** - JSON 路径解析
- **JsoupXpath** - XPath 解析扩展

### 媒体库
- **ExoPlayer** - 媒体播放器
- **NanoHttpd** - 轻量级 HTTP 服务器

### 工具库
- **Gson** - JSON 序列化
- **Commons Text** - 文本处理工具
- **Markwon** - Markdown 渲染

### UI 库
- **Material Components** - Material Design 组件
- **Flexbox** - 弹性布局
- **ZXing** - 二维码处理

## 封装原则

### 统一接口
为相似功能的第三方库提供统一接口，便于替换和维护。

### 配置管理
集中管理第三方库的配置参数，便于统一调整。

### 异常处理
统一封装第三方库的异常处理，提供标准错误信息。

### 性能优化
根据应用需求优化第三方库的使用方式。

## 使用方法

```kotlin
// 使用网络库
val response = okHttpClient.newCall(request).execute()

// 使用图片库
Glide.with(context).load(url).into(imageView)

// 使用数据库
appDb.bookDao.insert(book)

// 使用文本处理
val doc = Jsoup.parse(html)
```

## 注意事项

1. 第三方库版本应定期更新，修复安全漏洞
2. 避免直接在业务代码中使用第三方库 API
3. 对第三方库的依赖应尽量减少
4. 注意第三方库的许可协议
5. 监控第三方库的性能影响
6. 及时移除不再使用的第三方库