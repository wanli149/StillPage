# StillPage 核心业务逻辑与模块交互分析

## 核心业务流程

### 1. 书籍管理业务流程

#### 1.1 书籍导入流程
```
本地文件导入:
用户选择文件/文件夹 → ImportBookActivity
       ↓
扫描文件系统 → ImportBookViewModel.scanDoc()
       ↓
识别电子书格式 → Book模块解析器
       ↓
提取元数据 → EpubFile/MobiFile/UmdFile
       ↓
保存到数据库 → BookDao.insert()
       ↓
更新书架显示 → BookshelfFragment

网络书源导入:
用户搜索关键词 → SearchActivity
       ↓
调用书源规则 → Rhino脚本引擎执行
       ↓
解析搜索结果 → AnalyzeRule.getElements()
       ↓
用户选择书籍 → BookInfoActivity
       ↓
获取书籍详情 → 书源详情规则
       ↓
添加到书架 → BookDao.insert()
```

#### 1.2 书架管理流程
```
书架显示:
MainActivity启动 → BookshelfFragment
       ↓
加载书籍列表 → BookshelfViewModel.loadBooks()
       ↓
查询数据库 → BookDao.flowAll()
       ↓
按时间排序显示 → RecyclerView + BookAdapter

书籍操作:
长按书籍 → 显示操作菜单
       ↓
删除/置顶/分组 → BookDao.update()/delete()
       ↓
同步到WebDAV → Backup.backup()
       ↓
刷新界面 → LiveData观察者更新
```

### 2. 阅读业务流程

#### 2.1 阅读界面启动流程
```
用户点击书籍 → BookshelfFragment.openBook()
       ↓
启动阅读界面 → ReadBookActivity
       ↓
初始化阅读环境 → ReadBookViewModel.initBook()
       ↓
加载章节列表 → loadChapterList()
       ↓
定位阅读位置 → ReadBook.loadContent()
       ↓
渲染页面内容 → ChapterProvider.getTextPage()
       ↓
显示阅读界面 → ReadView.setContent()
```

#### 2.2 阅读进度管理
```
阅读进度变化:
用户翻页 → ReadView.onPageChange()
       ↓
更新进度信息 → ReadBook.saveRead()
       ↓
保存到数据库 → BookDao.update()
       ↓
定时备份 → startBackupJob()
       ↓
同步到WebDAV → AppWebDav.upload()

跨设备同步:
启动应用 → 检查WebDAV
       ↓
下载远程进度 → AppWebDav.download()
       ↓
比较时间戳 → 选择最新进度
       ↓
更新本地数据 → BookDao.update()
       ↓
恢复阅读位置 → ReadBook.skipToPage()
```

### 3. 书源管理业务流程

#### 3.1 书源导入流程
```
书源导入:
用户选择导入方式 → BookSourceActivity
       ↓
文件/网络/二维码 → ImportBookSourceDialog
       ↓
解析书源JSON → BookSource.fromJson()
       ↓
验证书源规则 → BookSourceDebugActivity
       ↓
保存到数据库 → BookSourceDao.insert()
       ↓
更新书源列表 → BookSourceAdapter.notifyDataSetChanged()
```

#### 3.2 书源规则执行
```
搜索书籍:
用户输入关键词 → SearchActivity
       ↓
遍历启用书源 → BookSourceDao.enabledBookSources
       ↓
执行搜索规则 → Rhino脚本引擎
       ↓
网络请求获取 → OkHttp + 书源URL规则
       ↓
解析搜索结果 → AnalyzeRule.getElements()
       ↓
返回搜索结果 → SearchBook列表

获取章节内容:
用户选择章节 → ReadBookActivity
       ↓
执行内容规则 → 书源content规则
       ↓
网络请求页面 → OkHttp + 章节URL
       ↓
解析页面内容 → AnalyzeRule.getString()
       ↓
内容处理 → ContentProcessor.getContent()
       ↓
显示章节内容 → ReadView.setContent()
```

## 模块交互关系

### 1. Android主应用与模块交互

#### 1.1 与Book模块交互
```
主应用 → Book模块:
- 电子书文件解析: EpubFile.getBookInfo()
- 章节内容提取: EpubFile.getChapterContent()
- 目录结构获取: EpubBook.getTableOfContents()
- 元数据读取: EpubBook.getMetadata()

Book模块 → 主应用:
- 解析结果回调: BookInfo对象
- 章节数据返回: BookChapter列表
- 错误信息反馈: Exception处理
```

#### 1.2 与Rhino模块交互
```
主应用 → Rhino模块:
- 脚本执行请求: RhinoScriptEngine.eval()
- 上下文设置: setChapter()/setCoroutineContext()
- 安全配置: RhinoClassShutter设置
- 变量绑定: ScriptBindings.put()

Rhino模块 → 主应用:
- 执行结果返回: JavaScript执行结果
- 异常信息: ScriptException
- 日志输出: console.log()处理
```

#### 1.3 与Web模块交互
```
主应用 → Web模块:
- HTTP API服务: WebService提供REST接口
- WebSocket服务: 实时搜索和通信
- 数据同步: 书架、进度、配置同步
- 文件服务: 静态资源访问

Web模块 → 主应用:
- API请求: 通过HTTP调用Android服务
- 实时通信: WebSocket双向通信
- 数据更新: 通过API更新Android数据
```

### 2. 数据流向图

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   用户界面层     │    │   业务逻辑层     │    │   数据访问层     │
│                │    │                │    │                │
│ Activity/       │    │ ViewModel/     │    │ Room Database  │
│ Fragment        │◄──►│ Repository     │◄──►│ Dao            │
│                │    │                │    │                │
│ Web Vue.js      │    │ Service/       │    │ SharedPref     │
│ Components      │    │ Manager        │    │                │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   外部模块      │    │   脚本引擎      │    │   网络层        │
│                │    │                │    │                │
│ Book Module     │    │ Rhino Engine    │    │ OkHttp         │
│ (EPUB/UMD/PDF)  │    │ (JavaScript)    │    │ Retrofit       │
│                │    │                │    │                │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 3. 关键接口定义

#### 3.1 书籍管理接口
```kotlin
// 书籍数据访问接口
interface BookDao {
    @Query("SELECT * FROM books ORDER BY durChapterTime DESC")
    fun flowAll(): Flow<List<Book>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg books: Book)
    
    @Update
    suspend fun update(vararg books: Book)
    
    @Delete
    suspend fun delete(vararg books: Book)
}

// 书籍业务逻辑接口
interface BookRepository {
    suspend fun getBookShelf(): List<Book>
    suspend fun saveBook(book: Book)
    suspend fun deleteBook(book: Book)
    suspend fun syncToWebDAV()
}
```

#### 3.2 阅读管理接口
```kotlin
// 阅读回调接口
interface ReadBook.CallBack {
    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)
    fun upView()
    fun upPageAnim()
    fun onPageChange()
    fun contentLoadFinish()
    fun upPageProgress()
}

// 章节内容提供接口
interface ContentProvider {
    suspend fun getChapterContent(
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String? = null,
        needSave: Boolean = true
    ): String
}
```

#### 3.3 书源管理接口
```kotlin
// 书源规则执行接口
interface AnalyzeRule {
    fun setContent(content: Any): AnalyzeRule
    fun getElements(rule: String): List<Any>
    fun getString(rule: String): String
    fun getStringList(rule: String): List<String>
}

// 脚本引擎接口
interface ScriptEngine {
    fun eval(script: String): Any?
    fun eval(script: String, bindings: Bindings): Any?
    fun compile(script: String): CompiledScript
}
```

## 代码规范与架构模式

### 1. 架构模式

#### 1.1 MVVM模式实现
```kotlin
// ViewModel层 - 业务逻辑处理
class BookshelfViewModel : BaseViewModel() {
    private val _books = MutableLiveData<List<Book>>()
    val books: LiveData<List<Book>> = _books
    
    fun loadBooks() {
        execute {
            _books.postValue(bookRepository.getBookShelf())
        }
    }
}

// View层 - UI展示
class BookshelfFragment : BaseBookshelfFragment() {
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel.books.observe(this) { books ->
            adapter.setItems(books)
        }
        viewModel.loadBooks()
    }
}

// Model层 - 数据模型
@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    var bookUrl: String = "",
    var name: String = "",
    var author: String = "",
    // ... 其他字段
)
```

#### 1.2 Repository模式
```kotlin
// Repository接口定义
interface BookRepository {
    suspend fun getBookShelf(): List<Book>
    suspend fun searchBook(key: String): List<SearchBook>
    suspend fun getBookInfo(book: SearchBook): Book
}

// Repository实现
class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val bookService: BookService
) : BookRepository {
    
    override suspend fun getBookShelf(): List<Book> {
        return bookDao.all
    }
    
    override suspend fun searchBook(key: String): List<SearchBook> {
        return bookService.searchBook(key)
    }
}
```

### 2. 代码组织规范

#### 2.1 包结构规范
```
io.stillpage.app/
├── data/                    # 数据层
│   ├── dao/                # 数据访问对象
│   ├── entities/           # 数据实体
│   └── AppDatabase.kt      # 数据库配置
├── model/                  # 业务模型
│   ├── analyzeRule/        # 规则解析
│   ├── localBook/          # 本地书籍
│   └── ReadBook.kt         # 阅读模型
├── ui/                     # 界面层
│   ├── main/               # 主界面
│   ├── book/               # 书籍相关
│   └── widget/             # 自定义控件
├── help/                   # 辅助工具
│   ├── book/               # 书籍处理
│   ├── config/             # 配置管理
│   └── storage/            # 存储管理
├── service/                # 后台服务
├── receiver/               # 广播接收器
└── utils/                  # 工具类
```

#### 2.2 命名规范
```kotlin
// 类命名：大驼峰命名法
class BookSourceActivity : BaseActivity()
class ReadBookViewModel : BaseViewModel()

// 方法命名：小驼峰命名法
fun loadBookShelf()
fun saveBookProgress()

// 变量命名：小驼峰命名法
private val bookAdapter: BookAdapter
private var isLoading: Boolean = false

// 常量命名：全大写下划线分隔
companion object {
    const val RESULT_DELETED = 101
    const val REQUEST_CODE_IMPORT = 102
}
```

#### 2.3 异步处理规范
```kotlin
// 使用Kotlin Coroutines
class BookViewModel : BaseViewModel() {
    fun loadBooks() {
        viewModelScope.launch {
            try {
                val books = withContext(Dispatchers.IO) {
                    bookRepository.getBookShelf()
                }
                _books.postValue(books)
            } catch (e: Exception) {
                _error.postValue(e.message)
            }
        }
    }
}

// Flow数据流处理
class BookDao {
    @Query("SELECT * FROM books")
    fun flowAll(): Flow<List<Book>>
}

// 在ViewModel中观察Flow
init {
    bookDao.flowAll()
        .flowOn(Dispatchers.IO)
        .catch { e -> _error.postValue(e.message) }
        .onEach { books -> _books.postValue(books) }
        .launchIn(viewModelScope)
}
```

### 3. 错误处理规范

#### 3.1 异常处理策略
```kotlin
// 统一异常处理基类
abstract class BaseViewModel : ViewModel() {
    protected val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    
    protected fun execute(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage)
                LogUtils.e("ViewModel", e)
            }
        }
    }
}

// 网络请求异常处理
class BookService {
    suspend fun searchBook(key: String): List<SearchBook> {
        return try {
            api.searchBook(key)
        } catch (e: IOException) {
            throw NetworkException("网络连接失败", e)
        } catch (e: HttpException) {
            throw ServerException("服务器错误: ${e.code()}", e)
        }
    }
}
```

#### 3.2 日志记录规范
```kotlin
// 统一日志工具
object LogUtils {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg)
        }
    }
    
    fun e(tag: String, throwable: Throwable) {
        Log.e(tag, throwable.message, throwable)
        // 生产环境可以上报到崩溃分析平台
    }
}

// 使用示例
class ReadBookActivity {
    private fun loadChapter() {
        LogUtils.d("ReadBook", "开始加载章节: ${chapter.title}")
        try {
            // 加载逻辑
        } catch (e: Exception) {
            LogUtils.e("ReadBook", e)
        }
    }
}
```

### 4. 性能优化规范

#### 4.1 内存管理
```kotlin
// 及时释放资源
class ReadBookActivity {
    override fun onDestroy() {
        super.onDestroy()
        ReadBook.book?.let {
            ReadBook.saveRead()
        }
        // 取消协程任务
        preDownloadTask?.cancel()
        // 清理缓存
        contentProcessor = null
    }
}

// 使用弱引用避免内存泄漏
class TimeBatteryReceiver : BroadcastReceiver() {
    private var activityRef: WeakReference<ReadBookActivity>? = null
    
    fun register(activity: ReadBookActivity) {
        activityRef = WeakReference(activity)
    }
}
```

#### 4.2 数据库优化
```kotlin
// 批量操作
@Dao
interface BookDao {
    @Insert
    suspend fun insertBooks(books: List<Book>)
    
    @Transaction
    suspend fun updateBooksWithChapters(
        books: List<Book>,
        chapters: List<BookChapter>
    ) {
        insertBooks(books)
        insertChapters(chapters)
    }
}

// 分页加载
@Query("SELECT * FROM books ORDER BY durChapterTime DESC LIMIT :limit OFFSET :offset")
suspend fun getBooksPaged(limit: Int, offset: Int): List<Book>
```

### 5. 安全规范

#### 5.1 脚本执行安全
```kotlin
// 限制脚本访问权限
class RhinoClassShutter : ClassShutter {
    override fun visibleToScripts(className: String): Boolean {
        return when {
            className.startsWith("java.lang.") -> true
            className.startsWith("java.util.") -> true
            className.startsWith("io.stillpage.app.model.") -> true
            else -> false
        }
    }
}

// 脚本执行超时控制
class RhinoScriptEngine {
    fun evalWithTimeout(script: String, timeout: Long): Any? {
        return runBlocking {
            withTimeout(timeout) {
                eval(script)
            }
        }
    }
}
```

#### 5.2 数据加密
```kotlin
// 敏感数据加密存储
object CryptoUtils {
    fun encrypt(data: String, key: String): String {
        // AES加密实现
    }
    
    fun decrypt(encryptedData: String, key: String): String {
        // AES解密实现
    }
}

// 使用示例
class BookSourceDao {
    fun saveBookSource(source: BookSource) {
        if (source.loginUrl.isNotEmpty()) {
            source.loginUrl = CryptoUtils.encrypt(source.loginUrl, getKey())
        }
        insert(source)
    }
}
```

这套代码规范确保了项目的可维护性、可扩展性和安全性，为团队协作提供了统一的标准。