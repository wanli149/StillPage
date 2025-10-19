# Book 模块

电子书处理模块，负责解析和处理各种电子书格式。

## 功能说明

该模块提供了对多种电子书格式的支持：
- EPUB 2.0/3.0 格式解析
- UMD 格式解析
- PDF 基础处理功能
- 其他文本格式处理

## 目录结构

```
book/
├── src/                    # 源代码目录
│   └── main/               # 主代码
│       ├── java/           # Java 源代码
│       │   ├── base/       # 基础类
│       │   ├── epublib/    # EPUB 处理库
│       │   └── umdlib/     # UMD 处理库
│       ├── resources/      # 资源文件
│       └── AndroidManifest.xml  # 模块配置文件
├── build.gradle            # 构建配置文件
└── README.md               # 本文件
```

## 核心类说明

### EPUB 处理
- `EpubBook` - EPUB 书籍核心类
- `EpubReader` - EPUB 文件读取器
- `EpubWriter` - EPUB 文件写入器

### UMD 处理
- `UmdBook` - UMD 书籍核心类
- `UmdReader` - UMD 文件读取器

### 基础类
- `BaseBook` - 书籍基础类
- `BookChapter` - 章节信息类

## 使用方法

```java
// 解析 EPUB 文件
EpubReader reader = new EpubReader();
EpubBook book = reader.readEpub(inputStream);

// 获取书籍信息
String title = book.getTitle();
String author = book.getAuthor();

// 获取章节内容
List<BookChapter> chapters = book.getChapters();
String content = chapters.get(0).getContent();
```

## 注意事项

1. 该模块为纯 Java 实现，不依赖 Android 特定 API
2. 处理大文件时需要注意内存使用情况
3. EPUB 解析基于自定义的 epublib 实现
4. UMD 格式支持可能不完整，需要根据实际需求完善
5. PDF 处理功能较为基础，复杂 PDF 可能无法正确解析