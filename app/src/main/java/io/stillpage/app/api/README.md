# API 模块

API 控制器模块，处理来自 Web 端的 REST API 请求。

## 功能说明

该模块实现了 Web 服务的 API 控制器，处理各种 REST 请求并返回相应数据。

## 目录结构

```
api/
├── controller/             # API 控制器
│   ├── BookController.kt   # 书籍相关控制器
│   ├── BookSourceController.kt  # 书源相关控制器
│   ├── RssSourceController.kt   # RSS 源相关控制器
│   └── ReplaceRuleController.kt # 替换规则控制器
├── ReturnData.kt           # 返回数据封装类
├── ReaderProvider.kt       # 内容提供者实现
└── README.md              # 本文件
```

## 核心控制器

### 书籍控制器 (BookController)
处理书籍相关 API 请求：
- 获取书架数据
- 获取章节列表
- 获取章节内容
- 保存书籍信息
- 删除书籍
- 保存阅读进度

### 书源控制器 (BookSourceController)
处理书源相关 API 请求：
- 获取书源列表
- 获取指定书源
- 保存书源
- 批量保存书源
- 删除书源

### RSS 源控制器 (RssSourceController)
处理 RSS 源相关 API 请求：
- 获取 RSS 源列表
- 获取指定 RSS 源
- 保存 RSS 源
- 批量保存 RSS 源
- 删除 RSS 源

### 替换规则控制器 (ReplaceRuleController)
处理替换规则相关 API 请求：
- 获取替换规则列表
- 保存替换规则
- 删除替换规则
- 测试替换规则

## 数据封装

### ReturnData
统一的 API 返回数据格式：
```json
{
  "isSuccess": true,
  "errorMsg": "",
  "data": {}
}
```

## 使用方法

```kotlin
// 处理获取书架请求
val result = BookController.bookshelf

// 处理保存书籍请求
val result = BookController.saveBook(postData)

// 处理搜索书籍请求
val result = BookSourceController.searchBook(parameters)
```

## 安全考虑

1. API 接口需要验证请求来源
2. 敏感操作需要身份验证
3. 输入数据需要验证和过滤
4. 防止 SQL 注入和 XSS 攻击
5. 限制请求频率防止滥用

## 注意事项

1. 控制器应保持无状态，避免持有上下文引用
2. 数据验证要完善，防止非法数据入库
3. 错误处理要统一，返回标准错误格式
4. 大数据传输应考虑分页或流式处理
5. 日志记录便于问题排查和监控