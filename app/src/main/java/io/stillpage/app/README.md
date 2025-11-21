# 文件结构介绍

* api - 提供的 REST API 接口控制器
* base - 基类和抽象实现
* constant - 常量定义
* data - 数据层（数据库实体和 DAO）
* exception - 自定义异常类
* help - 辅助工具类
* lib - 第三方库集成
* model - 业务模型和数据解析
* receiver - 广播接收器
* service - 后台服务
* ui - 用户界面组件
* utils - 工具类集合
* web - Web 服务实现

## 详细说明

### API 模块
包含 REST API 控制器，用于处理来自 Web 端的请求。

### Base 模块
包含应用程序的基础类，如 BaseActivity、BaseFragment、BaseViewModel 等。

### Constant 模块
定义应用程序中使用的各种常量，如偏好设置键、URL 模式等。

### Data 模块
包含 Room 数据库相关的实体类和数据访问对象（DAO）。

### Exception 模块
自定义异常类，用于处理应用程序中的特定错误情况。

### Help 模块
辅助工具类，提供各种功能支持，如书源帮助、配置管理等。

### Lib 模块
第三方库的集成和封装。

### Model 模块
业务模型类，用于数据解析和业务逻辑处理。

### Receiver 模块
广播接收器，用于处理系统广播和应用内广播。

### Service 模块
后台服务，如下载服务、朗读服务等。

### UI 模块
用户界面组件，包括 Activity、Fragment、自定义 View 等。

### Utils 模块
工具类集合，提供各种通用功能。

### Web 模块
Web 服务实现，包括 HTTP 服务器和 WebSocket 服务器。