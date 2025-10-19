# UI 模块

用户界面模块，包含应用程序的所有界面组件。

## 功能说明

该模块包含了应用程序的所有用户界面实现，包括 Activity、Fragment、自定义 View 等。

## 目录结构

```
ui/
├── main/                   # 主界面组件
│   ├── MainActivity.kt     # 主 Activity
│   ├── MainFragment.kt     # 主 Fragment
│   └── ...                 # 其他主界面组件
├── book/                   # 书籍相关界面
│   ├── ReadBookActivity.kt # 阅读界面
│   ├── BookDetailActivity.kt # 书籍详情界面
│   └── ...                 # 其他书籍界面
├── source/                 # 书源相关界面
│   ├── BookSourceActivity.kt # 书源管理界面
│   ├── BookSourceEditActivity.kt # 书源编辑界面
│   └── ...                 # 其他书源界面
├── widget/                 # 自定义控件
│   ├── ReadView.kt         # 阅读视图
│   ├── ChapterListView.kt  # 章节列表视图
│   └── ...                 # 其他自定义控本
├── dialog/                 # 对话框
├── adapter/                # 适配器
├── fragment/               # 碎片组件
└── README.md              # 本文件
```

## 核心界面组件

### 主界面
- `MainActivity` - 应用程序主入口界面
- `MainFragment` - 主界面内容 Fragment

### 阅读界面
- `ReadBookActivity` - 核心阅读界面
- `ReadView` - 阅读内容显示控件
- `ChapterListView` - 章节列表控件

### 书源管理界面
- `BookSourceActivity` - 书源管理主界面
- `BookSourceEditActivity` - 书源编辑界面
- `BookSourceDebugActivity` - 书源调试界面

### 自定义控件
- `ReadView` - 阅读视图，支持多种翻页模式
- `ChapterListView` - 章节列表显示
- `PageWidget` - 页面翻页控件

## 界面架构

采用 MVVM 架构模式：
- View (Activity/Fragment) - 负责 UI 显示和用户交互
- ViewModel - 负责业务逻辑处理和数据准备
- DataBinding - 实现数据绑定

## 使用方法

```kotlin
// 启动阅读界面
val intent = Intent(context, ReadBookActivity::class.java)
intent.putExtra("bookUrl", book.bookUrl)
startActivity(intent)

// 在 Fragment 中使用 ViewModel
val viewModel: BookViewModel by viewModels()
```

## 注意事项

1. 界面组件应遵循 Material Design 规范
2. 大型列表应使用 RecyclerView 优化性能
3. 图片加载应使用 Glide 等异步加载库
4. 界面更新应避免在主线程进行耗时操作
5. 注意内存泄漏问题，及时释放资源
6. 适配不同屏幕尺寸和分辨率