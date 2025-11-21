# Modules 模块目录

此目录包含 StillPage 项目的功能模块，每个模块都有特定的职责和功能。

## 目录结构

```
modules/
├── book/       # 电子书处理模块
├── rhino/      # JavaScript 引擎模块
└── web/        # Web 前端模块
```

## 模块说明

### Book 模块
- **功能**: 处理各种电子书格式（EPUB、UMD、PDF等）
- **主要类**: EpubBook、UmdBook 等格式解析器
- **依赖**: 无外部依赖，纯 Java 实现

### Rhino 模块
- **功能**: 集成 Mozilla Rhino JavaScript 引擎
- **主要类**: RhinoScriptEngine、ScriptBindings 等
- **用途**: 执行书源规则和自定义脚本
- **安全**: 实现了沙箱机制限制脚本访问权限

### Web 模块
- **功能**: Vue.js 实现的 Web 前端界面
- **技术栈**: Vue 3 + TypeScript + Pinia + Element Plus
- **通信**: 通过 HTTP REST API 和 WebSocket 与 Android 应用通信
- **用途**: 提供 Web 书架和书源编辑功能

## 模块间关系

```
┌─────────────┐    ┌─────────────┐
│   Android   │    │     Web     │
│     App     │    │   Module    │
└─────────────┘    └─────────────┘
       │                   │
       └─────────┬─────────┘
                 │
    ┌────────────┴────────────┐
    │      Rhino Module       │
    │  (JavaScript Engine)    │
    └────────────┬────────────┘
                 │
    ┌────────────┴────────────┐
    │      Book Module        │
    │   (Ebook Processing)    │
    └─────────────────────────┘
```

## 注意事项

1. 模块间通过接口进行通信，降低耦合度
2. 每个模块应尽量保持独立，减少相互依赖
3. 模块更新时需要考虑向后兼容性
4. Web 模块需要 Android 应用提供后端服务支持