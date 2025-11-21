# Rhino 模块

JavaScript 引擎模块，集成 Mozilla Rhino 用于执行书源规则和自定义脚本。

## 功能说明

该模块提供了安全的 JavaScript 执行环境，主要用于：
- 执行书源规则脚本
- 网页内容解析和数据提取
- 自定义脚本执行
- 提供沙箱安全机制

## 目录结构

```
rhino/
├── src/                    # 源代码目录
│   └── main/               # 主代码
│       ├── java/           # Java 源代码
│       │   ├── rhino/      # Rhino 引擎核心类
│       │   ├── AbstractScriptEngine.kt  # 抽象脚本引擎
│       │   ├── Bindings.kt              # 绑定接口
│       │   ├── Compilable.kt            # 可编译接口
│       │   ├── CompiledScript.kt        # 编译脚本类
│       │   ├── Invocable.kt             # 可调用接口
│       │   ├── RhinoContextFactory.kt   # Rhino 上下文工厂
│       │   ├── ScriptBindings.kt        # 脚本绑定
│       │   ├── ScriptBindingsExtensions.kt  # 脚本绑定扩展
│       │   ├── ScriptContext.kt         # 脚本上下文
│       │   ├── ScriptEngine.kt          # 脚本引擎接口
│       │   ├── ScriptException.kt       # 脚本异常
│       │   ├── SimpleBindings.kt        # 简单绑定实现
│       │   └── SimpleScriptContext.kt   # 简单脚本上下文
│       └── AndroidManifest.xml          # 模块配置文件
├── lib/                    # 第三方库
├── build.gradle            # 构建配置文件
└── README.md               # 本文件
```

## 核心类说明

### 脚本引擎
- `RhinoScriptEngine` - 主要脚本执行引擎
- `RhinoContextFactory` - Rhino 上下文工厂，用于创建安全的执行环境

### 安全控制
- `RhinoClassShutter` - 类访问控制，限制脚本可以访问的 Java 类
- `SecurityManager` - 安全管理器，控制文件系统和网络访问

### 绑定和上下文
- `ScriptBindings` - 脚本变量绑定
- `ScriptContext` - 脚本执行上下文
- `SimpleScriptContext` - 简单脚本上下文实现

## 使用方法

```kotlin
// 创建脚本引擎实例
val engine = RhinoScriptEngine()

// 设置变量绑定
val bindings = SimpleBindings()
bindings["variable"] = "value"

// 执行脚本
val result = engine.eval("variable + ' processed'", bindings)

// 使用安全沙箱
val shutter = RhinoClassShutter()
engine.setClassShutter(shutter)
```

## 安全机制

1. **ClassShutter**: 限制脚本访问的 Java 类
2. **SecurityManager**: 控制文件系统和网络访问
3. **沙箱环境**: 隔离脚本执行环境
4. **超时控制**: 防止脚本长时间执行

## 注意事项

1. 所有脚本执行都应在安全沙箱中进行
2. 需要严格控制脚本可以访问的 Java 类和方法
3. 对于网络请求应使用应用统一的网络层
4. 脚本执行应设置合理的超时时间
5. 避免在脚本中执行耗时操作
6. 定期更新 Rhino 引擎版本以修复安全漏洞