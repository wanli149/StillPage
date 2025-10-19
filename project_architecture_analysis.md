# StillPage 项目架构分析

## 项目概述
StillPage 是一个基于 Android 的阅读应用，支持多种电子书格式（EPUB、UMD、PDF等），具备书源管理、在线搜索、阅读进度同步等功能。项目采用模块化架构设计，包含主应用和多个功能模块。

## 技术栈分析

### Android 主应用
- **开发语言**: Kotlin + Java
- **架构模式**: MVVM (Model-View-ViewModel)
- **UI框架**: Android Jetpack + View Binding
- **导航组件**: BottomNavigationView + ViewPager + Fragment
- **异步处理**: Kotlin Coroutines
- **数据存储**: Room Database + SharedPreferences
- **网络请求**: OkHttp + Retrofit
- **依赖注入**: 自定义实现
- **脚本引擎**: Mozilla Rhino (JavaScript执行)

### Web 模块 (Vue.js)
- **前端框架**: Vue 3 + TypeScript
- **状态管理**: Pinia
- **路由管理**: Vue Router (Hash模式)
- **UI组件库**: Element Plus
- **构建工具**: Vite
- **HTTP客户端**: Axios
- **WebSocket**: 原生WebSocket API

### Book 模块 (电子书处理)
- **支持格式**: EPUB 2.0/3.0, UMD, PDF
- **核心库**: EPUBLib (自定义实现)
- **文件处理**: Java NIO + 自定义解析器

### Rhino 模块 (脚本引擎)
- **JavaScript引擎**: Mozilla Rhino
- **安全控制**: ClassShutter + SecurityManager
- **Java互操作**: 自定义Wrapper和Adapter

## 项目架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    StillPage 应用架构                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐    ┌─────────────────┐                │
│  │   Android App   │    │   Web Module    │                │
│  │   (主应用模块)    │    │   (Vue.js前端)   │                │
│  └─────────────────┘    └─────────────────┘                │
│           │                       │                        │
│           │              ┌────────┴────────┐               │
│           │              │                 │               │
│           │              ▼                 ▼               │
│  ┌─────────────────┐    ┌─────────────────┐               │
│  │  Book Module    │    │  Rhino Module   │               │
│  │  (电子书处理)     │    │  (脚本引擎)      │               │
│  └─────────────────┘    └─────────────────┘               │
│                                                             │
└─────────────────────────────────────────────────────────────┘

                              │
                              ▼
                    ┌─────────────────┐
                    │   外部服务接口    │
                    │ (书源/WebDAV等)  │
                    └─────────────────┘
```

## 模块详细分析

### 1. Android 主应用模块

#### 核心组件架构
```
MainActivity (主入口)
├── BottomNavigationView (底部导航)
├── ViewPager (页面容器)
└── Fragment管理
    ├── BookshelfFragment (书架)
    ├── ExploreFragment (发现)
    ├── RSSFragment (RSS订阅)
    └── MyFragment (个人中心)
```

#### 关键Activity
- **MainActivity**: 应用主界面，管理底部导航和Fragment切换
- **ReadBookActivity**: 核心阅读界面，支持多种阅读模式和配置
- **BookSourceActivity**: 书源管理界面，支持导入/导出/调试
- **BookSourceEditActivity**: 书源编辑界面，支持规则配置

#### 数据层架构
```
Repository Pattern
├── Local Data Source (Room Database)
│   ├── BookDao (书籍数据)
│   ├── BookSourceDao (书源数据)
│   └── ReadRecordDao (阅读记录)
├── Remote Data Source (网络API)
│   ├── BookSourceService (书源服务)
│   └── WebDAVService (同步服务)
└── Cache Layer (内存缓存)
```

### 2. Web 模块 (Vue.js前端)

#### 页面结构
```
Web应用路由
├── / (书架页面 - BookShelf.vue)
├── /chapter (章节阅读 - BookChapter.vue)
├── /bookSource (书源管理 - SourceEditor.vue)
└── /rssSource (RSS源管理 - SourceEditor.vue)
```

#### 状态管理 (Pinia Stores)
- **bookStore**: 管理书籍数据、阅读进度、配置信息
- **sourceStore**: 管理书源和RSS源数据
- **connectionStore**: 管理与Android应用的连接状态

#### API接口设计
```typescript
// 主要API接口
- getBookShelf(): 获取书架数据
- getChapterList(bookUrl): 获取章节列表
- getBookContent(bookUrl, chapterIndex): 获取章节内容
- saveBookProgress(progress): 保存阅读进度
- search(keyword): WebSocket搜索书籍
- getSources(): 获取书源列表
- saveSource(source): 保存书源配置
```

### 3. Book 模块 (电子书处理)

#### 支持的电子书格式
- **EPUB**: 完整支持EPUB 2.0和3.0标准
- **UMD**: 支持UMD格式解析
- **PDF**: 基础PDF文件处理

#### 核心类结构
```java
EpubBook (EPUB书籍核心类)
├── Resources (资源管理)
├── Metadata (元数据)
├── Spine (阅读顺序)
├── TableOfContents (目录)
└── Guide (导航指南)
```

### 4. Rhino 模块 (JavaScript脚本引擎)

#### 脚本执行环境
- **RhinoScriptEngine**: 主要脚本执行引擎
- **安全控制**: 通过ClassShutter限制Java类访问
- **上下文管理**: 独立的脚本执行上下文
- **对象包装**: 自定义Java对象包装器

#### 主要功能
- 执行书源规则脚本
- 网页内容解析
- 数据提取和转换
- 安全沙箱执行环境

## 数据流架构

### 阅读流程数据流
```
用户选择书籍 → BookShelf
       ↓
加载章节列表 → ChapterList API
       ↓
获取章节内容 → BookContent API
       ↓
渲染阅读界面 → ReadBookActivity/BookChapter.vue
       ↓
保存阅读进度 → BookProgress API
```

### 书源管理数据流
```
导入书源 → BookSourceEditActivity
     ↓
验证规则 → Rhino脚本引擎
     ↓
保存配置 → BookSourceDao
     ↓
同步到Web → SourceEditor.vue
```

## 安全架构

### 脚本执行安全
- **ClassShutter**: 限制脚本访问的Java类
- **SecurityManager**: 控制文件系统和网络访问
- **沙箱环境**: 隔离脚本执行环境

### 数据安全
- **本地加密**: 敏感数据本地加密存储
- **网络安全**: HTTPS通信，证书验证
- **权限控制**: 最小权限原则

## 性能优化策略

### Android应用优化
- **懒加载**: Fragment和数据按需加载
- **内存管理**: 及时释放大对象，避免内存泄漏
- **数据库优化**: 索引优化，批量操作
- **图片缓存**: 封面图片缓存机制

### Web应用优化
- **代码分割**: 路由级别的代码分割
- **状态缓存**: Pinia状态持久化
- **API缓存**: 接口数据缓存策略
- **虚拟滚动**: 大列表性能优化

## 扩展性设计

### 模块化架构
- **松耦合**: 模块间通过接口通信
- **插件化**: 支持动态加载功能模块
- **配置驱动**: 通过配置文件控制功能开关

### 书源扩展
- **规则引擎**: 基于JavaScript的灵活规则系统
- **多协议支持**: HTTP/HTTPS/WebSocket等
- **格式扩展**: 易于添加新的电子书格式支持

## 部署架构

### 开发环境
- **Android Studio**: 主应用开发
- **VS Code**: Web模块开发
- **Gradle**: 构建系统
- **Vite**: Web模块构建

### 构建流程
```
1. Android模块编译 (Gradle)
2. Web模块构建 (Vite)
3. 资源打包整合
4. APK生成和签名
5. 版本发布
```

这个架构设计确保了应用的可维护性、可扩展性和性能，同时提供了良好的用户体验和开发体验。