# StillPage 阅读器

StillPage 是一个开源的 Android 阅读应用程序，支持多种电子书格式（EPUB、UMD、PDF 等），具备书源管理、在线搜索、阅读进度同步等功能。

## 项目结构

```
.
├── app/                    # 主应用程序模块
├── modules/                # 功能模块
│   ├── book/               # 电子书处理模块
│   ├── rhino/              # JavaScript 引擎模块
│   └── web/                # Web 前端模块
├── 设计/                   # 设计相关文件
└── README.md              # 项目说明文件
```

## 主要功能

1. 自定义书源，自己设置规则，抓取网页数据，规则简单易懂
2. 列表书架，网格书架自由切换
3. 书源规则支持搜索及发现，所有找书看书功能全部自定义
4. 订阅内容，可以订阅想看的任何内容
5. 支持替换净化，去除广告替换内容很方便
6. 支持本地 TXT、EPUB 阅读，手动浏览，智能扫描
7. 支持高度自定义阅读界面，切换字体、颜色、背景等
8. 支持多种翻页模式，覆盖、仿真、滑动、滚动等
9. 软件开源，持续优化，无广告

## 技术栈

- **开发语言**: Kotlin + Java
- **架构模式**: MVVM (Model-View-ViewModel)
- **UI框架**: Android Jetpack + View Binding
- **异步处理**: Kotlin Coroutines
- **数据存储**: Room Database + SharedPreferences
- **网络请求**: OkHttp + Retrofit
- **脚本引擎**: Mozilla Rhino (JavaScript执行)
- **前端框架**: Vue 3 + TypeScript (Web模块)

## 目录说明

- `app/` - 主Android应用程序代码
- `modules/book/` - 电子书解析和处理功能
- `modules/rhino/` - JavaScript引擎实现
- `modules/web/` - Vue.js前端界面
- `设计/` - 设计相关文档和HTML文件

## 开发环境

- Android Studio
- Node.js (用于Web模块)
- JDK 17+

## 构建说明

```bash
# 构建Android应用
./gradlew assembleDebug

# 构建Web模块
cd modules/web
pnpm build
```

## 注意事项

1. 本软件不提供内容，需要用户自己手动添加书源
2. 书源规则使用JavaScript编写，请注意安全性
3. Web模块需要Android应用提供后端服务支持