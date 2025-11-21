# Exception 模块

自定义异常模块，包含应用程序中定义的各种异常类。

## 功能说明

该模块定义了应用程序中可能发生的各种异常情况，便于统一处理和错误诊断。

## 目录结构

```
exception/
├── AppException.kt         # 应用程序基础异常
├── BookSourceException.kt  # 书源相关异常
├── ContentException.kt     # 内容处理异常
├── NetworkException.kt     # 网络异常
├── ParserException.kt      # 解析异常
├── SecurityException.kt    # 安全异常
├── StorageException.kt     # 存储异常
└── README.md              # 本文件
```

## 异常类说明

### AppException
应用程序基础异常类，其他异常类的基类。

### BookSourceException
书源相关异常：
- 书源格式错误
- 书源解析失败
- 书源连接超时
- 书源验证失败

### ContentException
内容处理异常：
- 内容解析错误
- 内容格式不支持
- 内容解密失败
- 内容过滤错误

### NetworkException
网络异常：
- 网络连接失败
- 网络超时
- 网络协议错误
- 服务器错误

### ParserException
解析异常：
- HTML 解析错误
- JSON 解析错误
- XML 解析错误
- XPath 解析错误

### SecurityException
安全异常：
- 权限不足
- 访问被拒绝
- 安全验证失败
- 脚本执行异常

### StorageException
存储异常：
- 数据库操作失败
- 文件读写错误
- 存储空间不足
- 备份恢复失败

## 使用方法

```kotlin
// 抛出自定义异常
throw BookSourceException("书源格式错误")

// 捕获特定异常
try {
    // 可能抛出异常的代码
} catch (e: NetworkException) {
    // 处理网络异常
} catch (e: AppException) {
    // 处理其他应用异常
}

// 异常链处理
try {
    // 底层操作
} catch (e: IOException) {
    throw StorageException("文件读取失败", e)
}
```

## 设计原则

1. 异常类应具有明确的语义，便于识别和处理
2. 异常信息应包含足够的上下文信息，便于问题诊断
3. 异常类应支持异常链，保留原始异常信息
4. 避免过度细分异常类，保持合理的抽象层次
5. 异常处理应遵循最小惊讶原则

## 注意事项

1. 异常应仅用于异常情况，不应作为正常流程控制手段
2. 异常信息不应包含敏感数据
3. 异常处理要考虑性能影响，避免频繁异常抛出
4. 异常日志应包含足够的上下文信息
5. 异常处理要保持用户体验，避免应用崩溃