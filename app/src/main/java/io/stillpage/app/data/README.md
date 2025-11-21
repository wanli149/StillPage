# 数据存储模块

该模块负责应用程序的数据存储和访问，使用 Room 数据库作为主要存储方案。

## 目录结构

```
data/
├── dao/           # 数据访问对象
├── entities/      # 数据实体类
└── README.md      # 本文件
```

## 数据实体 (Entities)

* `Book` - 书籍信息
* `BookChapter` - 目录信息（章节）
* `BookGroup` - 书籍分组
* `Bookmark` - 书签
* `BookSource` - 书源
* `Cookie` - HTTP Cookie
* `ReplaceRule` - 替换规则
* `RssArticle` - RSS条目
* `RssReadRecord` - RSS阅读记录
* `RssSource` - RSS源
* `RssStar` - RSS收藏
* `SearchBook` - 搜索结果
* `SearchKeyword` - 搜索关键字
* `TxtTocRule` - TXT文件目录规则

## 数据访问对象 (DAO)

每个实体都有对应的 DAO 接口，提供数据操作方法：
* 插入 (Insert)
* 更新 (Update)
* 删除 (Delete)
* 查询 (Query)

## 数据库配置

* 数据库名称: `stillpage.db`
* 数据库版本: 76 (需要添加迁移脚本)
* 使用 Room 持久化库
* 支持数据库加密

## 使用示例

```kotlin
// 插入书籍
appDb.bookDao.insert(book)

// 查询所有书籍
val books = appDb.bookDao.all

// 更新书籍信息
appDb.bookDao.update(book)

// 删除书籍
appDb.bookDao.delete(book)
```

## 注意事项

1. 数据库版本升级时必须提供迁移脚本
2. 大量数据操作应使用事务处理
3. 数据查询应避免在主线程执行
4. 敏感数据应进行加密存储
5. 定期清理过期数据以节省存储空间