# Receiver 模块

广播接收器模块，处理系统和应用内广播消息。

## 功能说明

该模块包含了应用程序中使用的各种广播接收器，用于响应系统事件和应用内通知。

## 目录结构

```
receiver/
├── BatteryReceiver.kt      # 电池状态接收器
├── NetworkReceiver.kt      # 网络状态接收器
├── BootReceiver.kt         # 开机启动接收器
├── MediaButtonReceiver.kt  # 媒体按钮接收器
└── README.md              # 本文件
```

## 核心接收器

### 电池状态接收器 (BatteryReceiver)
监听电池状态变化：
- 电池电量变化
- 充电状态变化
- 电池温度变化
- 低电量警告

### 网络状态接收器 (NetworkReceiver)
监听网络连接状态：
- 网络连接建立
- 网络连接断开
- 网络类型变化（WiFi/移动数据）
- 网络可用性检查

### 开机启动接收器 (BootReceiver)
处理系统启动完成事件：
- 应用自启动
- 服务自动恢复
- 定时任务重启
- 数据同步初始化

### 媒体按钮接收器 (MediaButtonReceiver)
处理媒体控制按钮事件：
- 播放/暂停控制
- 上一曲/下一曲
- 音量调节
- 耳机插拔检测

## 广播类型

### 系统广播
- `Intent.ACTION_BATTERY_CHANGED` - 电池状态变化
- `ConnectivityManager.CONNECTIVITY_ACTION` - 网络状态变化
- `Intent.ACTION_BOOT_COMPLETED` - 系统启动完成
- `Intent.ACTION_HEADSET_PLUG` - 耳机插拔

### 应用内广播
- 自定义事件通知
- 数据更新通知
- 服务状态变化
- 用户操作反馈

## 使用方法

```kotlin
// 注册广播接收器
val receiver = BatteryReceiver()
val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
context.registerReceiver(receiver, filter)

// 发送自定义广播
val intent = Intent("com.example.CUSTOM_ACTION")
intent.putExtra("data", value)
context.sendBroadcast(intent)

// 动态注册接收器
class MyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 处理接收到的广播
    }
}
```

## 权限要求

部分广播接收器需要在 AndroidManifest.xml 中声明权限：
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.BATTERY_STATS" />
```

## 注意事项

1. 广播接收器中的操作应尽快完成，避免阻塞主线程
2. 长时间运行的任务应启动服务处理
3. 及时注销动态注册的接收器，避免内存泄漏
4. 处理各种异常情况，确保接收器稳定性
5. 注意 Android 8.0 以上版本对隐式广播的限制
6. 广播数据传递应避免传递大量数据