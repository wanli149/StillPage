# Model 模块

业务模型模块，包含数据解析和业务逻辑处理类。

## 功能说明

该模块包含了应用程序的核心业务模型和数据解析逻辑。

## 目录结构

```
model/
├── analyzeRule/            # 规则解析器
├── localBook/              # 本地书籍处理
├── ReadBook.kt            # 阅读模型
├── BookChapter.kt         # 章节模型
├── SearchBook.kt          # 搜索书籍模型
└── README.md              # 本文件
```

## 核心类说明

### 规则解析器 (analyzeRule/)
- `AnalyzeRule` - 规则解析核心类
- `AnalyzeByJSoup` - 使用 JSoup 解析网页内容
- `AnalyzeByXPath` - 使用 XPath 解析网页内容
- `AnalyzeByJSonPath` - 使用 JsonPath 解析 JSON 数据

### 阅读模型
- `ReadBook` - 阅读核心模型，管理阅读状态和进度
- `ChapterProvider` - 章节内容提供者
- `ContentProcessor` - 内容处理器

### 书籍模型
- `Book` - 书籍信息模型
- `BookChapter` - 章节信息模型
- `SearchBook` - 搜索结果模型

## 功能特点

### 规则解析
支持多种解析方式：
1. JSoup - HTML 文档解析
2. XPath - XML 路径表达式
3. JsonPath - JSON 数据解析
4. JavaScript - 自定义脚本解析

### 内容处理
- 文本净化和替换
- 图片链接处理
- 章节分割和重组
- 格式转换

## 使用方法

```kotlin
// 使用规则解析器
val analyzer = AnalyzeRule()
val result = analyzer.getString(rule)

// 处理章节内容
val content = ContentProcessor.getContent(book, chapter)

// 管理阅读进度
ReadBook.saveRead()
```

## 注意事项

1. 规则解析可能涉及网络请求，需要异步处理
2. 解析结果需要缓存以提高性能
3. 错误处理要完善，避免解析失败导致应用崩溃
4. 大文本处理要注意内存使用情况
5. JavaScript 执行需要在安全沙箱中进行