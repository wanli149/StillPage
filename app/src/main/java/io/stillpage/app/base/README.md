# Base 模块

基础类模块，包含应用程序的基类和抽象实现。

## 功能说明

该模块提供了应用程序中各个组件的基类实现，确保代码的一致性和可维护性。

## 目录结构

```
base/
├── BaseActivity.kt          # Activity 基类
├── BaseBindingActivity.kt   # 带数据绑定的 Activity 基类
├── BaseDialogFragment.kt    # DialogFragment 基类
├── BaseFragment.kt          # Fragment 基类
├── BaseViewModel.kt         # ViewModel 基类
├── BaseRepository.kt        # Repository 基类
├── BaseAdapter.kt           # RecyclerView Adapter 基类
├── BaseViewHolder.kt        # RecyclerView ViewHolder 基类
├── BaseService.kt           # Service 基类
├── BaseReceiver.kt          # BroadcastReceiver 基类
└── README.md                # 本文件
```

## 核心类说明

### BaseActivity
所有 Activity 的基类，提供：
- 生命周期管理
- 权限请求处理
- 统一的错误处理
- 基础 UI 操作方法

### BaseFragment
所有 Fragment 的基类，提供：
- 生命周期管理
- 数据绑定支持
- 与宿主 Activity 的通信机制

### BaseViewModel
所有 ViewModel 的基类，提供：
- 协程作用域管理
- 统一的错误处理
- 基础状态管理

### BaseRepository
数据仓库基类，提供：
- 数据源管理
- 统一的数据访问接口
- 缓存机制

## 使用方法

```kotlin
// 继承 BaseActivity
class MainActivity : BaseActivity() {
    // 实现具体功能
}

// 继承 BaseViewModel
class MainViewModel : BaseViewModel() {
    // 实现业务逻辑
}
```

## 注意事项

1. 所有 UI 组件应继承对应的基类
2. 基类中实现的功能应保持通用性
3. 避免在基类中添加过多具体业务逻辑
4. 基类的修改可能影响所有子类，需要充分测试