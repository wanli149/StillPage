# Constant 模块

常量定义模块，包含应用程序中使用的所有常量。

## 功能说明

该模块集中管理应用程序中使用的各种常量，包括偏好设置键、URL 模式、状态码等。

## 目录结构

```
constant/
├── AppConst.kt             # 应用程序常量
├── PreferKey.kt            # 偏好设置键
├── BookType.kt             # 书籍类型常量
├── PageAnim.kt             # 翻页动画常量
├── AppPattern.kt           # 正则表达式模式
├── BookSourceType.kt       # 书源类型常量
├── Theme.kt                # 主题常量
├── RssSort.kt             # RSS 排序常量
├── ReadAloudConst.kt      # 朗读常量
├── Backup.kt              # 备份常量
├── AppLog.kt              # 日志常量
├── EventBus.kt            # 事件总线常量
├── Action.kt              # 动作常量
└── README.md              # 本文件
```

## 常量分类

### 应用程序常量 (AppConst)
- 应用标识符
- 通知渠道 ID
- 默认配置值
- 系统路径

### 偏好设置键 (PreferKey)
- 用户设置项键名
- 配置选项标识
- 功能开关控制

### 书籍类型 (BookType)
- 文本类型
- 音频类型
- 图片类型
- 文件类型

### 翻页动画 (PageAnim)
- 覆盖翻页
- 仿真翻页
- 滑动翻页
- 滚动翻页

### 正则表达式模式 (AppPattern)
- URL 匹配模式
- 内容过滤模式
- 格式验证模式
- 分组分割模式

### 书源类型 (BookSourceType)
- 文本书源
- 音频书源
- 图片书源
- 文件书源

## 使用方法

```kotlin
// 使用偏好设置键
val autoRefresh = getPrefBoolean(PreferKey.autoRefresh, true)

// 使用书籍类型常量
if (book.type == BookType.audio) {
    // 处理音频书籍
}

// 使用正则表达式模式
if (url.matches(AppPattern.urlPattern)) {
    // 处理有效 URL
}
```

## 设计原则

1. 常量命名应具有明确语义，便于理解和维护
2. 相关联的常量应分组管理
3. 常量值应避免硬编码，使用有意义的标识符
4. 常量应具有良好的文档说明
5. 避免重复定义相同含义的常量

## 注意事项

1. 常量值的修改可能影响整个应用程序行为
2. 新增常量应考虑向后兼容性
3. 常量使用应保持一致性
4. 避免在常量中存储敏感信息
5. 常量文档应及时更新，保持准确性