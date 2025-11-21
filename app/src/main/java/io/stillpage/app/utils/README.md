# Utils 模块

工具类模块，提供各种通用功能实现。

## 功能说明

该模块包含了应用程序中使用的各种工具类，提供通用功能支持。

## 目录结构

```
utils/
├── AssetUtils.kt           # 资源文件工具
├── CacheUtils.kt           # 缓存工具
├── ChineseUtils.kt         # 中文处理工具
├── FileUtils.kt            # 文件操作工具
├── GsonUtils.kt            # JSON 处理工具
├── HttpUtils.kt            # HTTP 请求工具
├── ImageUtils.kt           # 图片处理工具
├── LogUtils.kt             # 日志工具
├── NetworkUtils.kt         # 网络工具
├── StringUtils.kt          # 字符串处理工具
├── ThreadUtils.kt          # 线程工具
└── README.md              # 本文件
```

## 核心工具类

### 日志工具 (LogUtils)
- 统一日志输出格式
- 支持不同日志级别
- 生产环境日志控制

### 网络工具 (HttpUtils)
- HTTP 请求封装
- 网络状态检测
- 请求超时处理

### JSON 工具 (GsonUtils)
- JSON 序列化和反序列化
- 泛型类型处理
- 错误处理

### 文件工具 (FileUtils)
- 文件读写操作
- 文件路径处理
- 文件类型判断

### 图片工具 (ImageUtils)
- 图片压缩和裁剪
- 图片格式转换
- 图片缓存管理

### 中文工具 (ChineseUtils)
- 简繁体转换
- 中文分词处理
- 中文编码处理

## 使用方法

```kotlin
// 使用日志工具
LogUtils.d("tag", "debug message")

// 使用网络工具
val response = HttpUtils.get(url)

// 使用 JSON 工具
val json = GsonUtils.toJson(obj)
val obj = GsonUtils.fromJson(json, clazz)

// 使用文件工具
val content = FileUtils.readText(file)
```

## 设计原则

1. 工具类方法应保持静态，便于调用
2. 方法功能应单一明确，避免复杂逻辑
3. 异常情况应妥善处理，避免应用崩溃
4. 性能优化，避免不必要的资源消耗
5. 代码复用，减少重复实现

## 注意事项

1. 工具类不应持有上下文引用，避免内存泄漏
2. 涉及文件操作的方法需要处理权限问题
3. 网络相关工具需要处理各种网络异常
4. 大数据处理要注意内存使用情况
5. 工具类的修改可能影响整个应用，需要充分测试