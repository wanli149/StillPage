# App 模块

这是 StillPage 的主应用程序模块，包含了 Android 应用的核心功能实现。

## 目录结构

```
app/
├── src/                    # 源代码目录
│   ├── main/               # 主代码
│   │   ├── java/           # Java/Kotlin 源代码
│   │   ├── res/            # 资源文件
│   │   └── AndroidManifest.xml  # 应用配置文件
│   └── test/               # 测试代码
├── schemas/                # Room 数据库模式
├── build.gradle            # 构建配置文件
└── README.md               # 本文件
```

## 主要组件

### 核心类
- `App.kt` - 应用程序入口点和全局配置

### 功能模块
- `api/` - 提供的 REST API 接口控制器
- `base/` - 基类和抽象实现
- `constant/` - 常量定义
- `data/` - 数据层（数据库实体和 DAO）
- `exception/` - 自定义异常类
- `help/` - 辅助工具类
- `lib/` - 第三方库集成
- `model/` - 业务模型和数据解析
- `receiver/` - 广播接收器
- `service/` - 后台服务
- `ui/` - 用户界面组件
- `utils/` - 工具类集合
- `web/` - Web 服务实现

## 构建配置

使用 Gradle 构建系统，主要依赖包括：
- Room 数据库
- OkHttp 网络请求
- Rhino JavaScript 引擎
- Glide 图片加载
- 其他 Android Jetpack 组件

## 注意事项

1. 数据库版本更新需要在 `AppDatabase.kt` 中添加迁移脚本
2. 新增权限需要在 `AndroidManifest.xml` 中声明
3. 网络请求需要遵循 Android 的网络安全配置
4. 所有异步操作应使用 Kotlin Coroutines