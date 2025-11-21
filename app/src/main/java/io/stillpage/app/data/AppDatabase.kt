package io.stillpage.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.stillpage.app.data.dao.BookChapterDao
import io.stillpage.app.data.dao.BookDao
import io.stillpage.app.data.dao.BookGroupDao
import io.stillpage.app.data.dao.BookSourceDao
import io.stillpage.app.data.dao.BookmarkDao
import io.stillpage.app.data.dao.CacheDao
import io.stillpage.app.data.dao.CookieDao
import io.stillpage.app.data.dao.DictRuleDao
import io.stillpage.app.data.dao.HttpTTSDao
import io.stillpage.app.data.dao.KeyboardAssistsDao
import io.stillpage.app.data.dao.ReadRecordDao
import io.stillpage.app.data.dao.ReplaceRuleDao
import io.stillpage.app.data.dao.RssArticleDao
import io.stillpage.app.data.dao.RssReadRecordDao
import io.stillpage.app.data.dao.RssSourceDao
import io.stillpage.app.data.dao.RssStarDao
import io.stillpage.app.data.dao.RuleSubDao
import io.stillpage.app.data.dao.SearchBookDao
import io.stillpage.app.data.dao.SearchKeywordDao
import io.stillpage.app.data.dao.ServerDao
import io.stillpage.app.data.dao.TxtTocRuleDao
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookGroup
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.BookSourcePart
import io.stillpage.app.data.entities.Bookmark
import io.stillpage.app.data.entities.Cache
import io.stillpage.app.data.entities.Cookie
import io.stillpage.app.data.entities.DictRule
import io.stillpage.app.data.entities.HttpTTS
import io.stillpage.app.data.entities.KeyboardAssist
import io.stillpage.app.data.entities.ReadRecord
import io.stillpage.app.data.entities.ReplaceRule
import io.stillpage.app.data.entities.RssArticle
import io.stillpage.app.data.entities.RssReadRecord
import io.stillpage.app.data.entities.RssSource
import io.stillpage.app.data.entities.RssStar
import io.stillpage.app.data.entities.RuleSub
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.data.entities.SearchKeyword
import io.stillpage.app.data.entities.Server
import io.stillpage.app.data.entities.TxtTocRule
import io.stillpage.app.help.DefaultData
import org.intellij.lang.annotations.Language
import splitties.init.appCtx
import java.util.Locale

val appDb by lazy {
    Room.databaseBuilder(appCtx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigrationFrom(false, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        .addMigrations(*DatabaseMigrations.migrations)
        .allowMainThreadQueries()
        .addCallback(AppDatabase.dbCallback)
        .build()
}

@Database(
    version = 76,
    exportSchema = true,
    entities = [Book::class, BookGroup::class, BookSource::class, BookChapter::class,
        ReplaceRule::class, SearchBook::class, SearchKeyword::class, Cookie::class,
        RssSource::class, Bookmark::class, RssArticle::class, RssReadRecord::class,
        RssStar::class, TxtTocRule::class, ReadRecord::class, HttpTTS::class, Cache::class,
        RuleSub::class, DictRule::class, KeyboardAssist::class, Server::class],
    views = [BookSourcePart::class],
    autoMigrations = [
        AutoMigration(from = 43, to = 44),
        AutoMigration(from = 44, to = 45),
        AutoMigration(from = 45, to = 46),
        AutoMigration(from = 46, to = 47),
        AutoMigration(from = 47, to = 48),
        AutoMigration(from = 48, to = 49),
        AutoMigration(from = 49, to = 50),
        AutoMigration(from = 50, to = 51),
        AutoMigration(from = 51, to = 52),
        AutoMigration(from = 52, to = 53),
        AutoMigration(from = 53, to = 54),
        AutoMigration(from = 54, to = 55, spec = DatabaseMigrations.Migration_54_55::class),
        AutoMigration(from = 55, to = 56),
        AutoMigration(from = 56, to = 57),
        AutoMigration(from = 57, to = 58),
        AutoMigration(from = 58, to = 59),
        AutoMigration(from = 59, to = 60),
        AutoMigration(from = 60, to = 61),
        AutoMigration(from = 61, to = 62),
        AutoMigration(from = 62, to = 63),
        AutoMigration(from = 63, to = 64),
        AutoMigration(from = 64, to = 65, spec = DatabaseMigrations.Migration_64_65::class),
        AutoMigration(from = 65, to = 66),
        AutoMigration(from = 66, to = 67),
        AutoMigration(from = 67, to = 68),
        AutoMigration(from = 68, to = 69),
        AutoMigration(from = 69, to = 70),
        AutoMigration(from = 70, to = 71),
        AutoMigration(from = 71, to = 72),
        AutoMigration(from = 72, to = 73),
        AutoMigration(from = 73, to = 74),
        AutoMigration(from = 74, to = 75),
        AutoMigration(from = 75, to = 76),
    ]
)
abstract class AppDatabase : RoomDatabase() {

    abstract val bookDao: BookDao
    abstract val bookGroupDao: BookGroupDao
    abstract val bookSourceDao: BookSourceDao
    abstract val bookChapterDao: BookChapterDao
    abstract val replaceRuleDao: ReplaceRuleDao
    abstract val searchBookDao: SearchBookDao
    abstract val searchKeywordDao: SearchKeywordDao
    abstract val rssSourceDao: RssSourceDao
    abstract val bookmarkDao: BookmarkDao
    abstract val rssArticleDao: RssArticleDao
    abstract val rssStarDao: RssStarDao
    abstract val rssReadRecordDao: RssReadRecordDao
    abstract val cookieDao: CookieDao
    abstract val txtTocRuleDao: TxtTocRuleDao
    abstract val readRecordDao: ReadRecordDao
    abstract val httpTTSDao: HttpTTSDao
    abstract val cacheDao: CacheDao
    abstract val ruleSubDao: RuleSubDao
    abstract val dictRuleDao: DictRuleDao
    abstract val keyboardAssistsDao: KeyboardAssistsDao
    abstract val serverDao: ServerDao

    companion object {

        const val DATABASE_NAME = "stillpage.db"

        const val BOOK_TABLE_NAME = "books"
        const val BOOK_SOURCE_TABLE_NAME = "book_sources"
        const val RSS_SOURCE_TABLE_NAME = "rssSources"

        val dbCallback = object : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                // 移除setLocale调用以避免数据库锁定问题
                Log.d("AppDatabaseCallback", "数据库创建完成，跳过setLocale以避免锁定问题")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                @Language("sql")
                val insertBookGroupAllSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdAll}, '全部', -10, 1
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdAll})
                """.trimIndent()
                db.execSQL(insertBookGroupAllSql)
                @Language("sql")
                val insertBookGroupLocalSql = """
                    insert into book_groups(groupId, groupName, 'order', enableRefresh, show) 
                    select ${BookGroup.IdLocal}, '本地', -9, 0, 1
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdLocal})
                """.trimIndent()
                db.execSQL(insertBookGroupLocalSql)
                @Language("sql")
                val insertBookGroupMusicSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdAudio}, '音频', -8, 1
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdAudio})
                """.trimIndent()
                db.execSQL(insertBookGroupMusicSql)
                @Language("sql")
                val insertBookGroupNetNoneGroupSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdNetNone}, '网络未分组', -7, 1
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdNetNone})
                """.trimIndent()
                db.execSQL(insertBookGroupNetNoneGroupSql)
                @Language("sql")
                val insertBookGroupLocalNoneGroupSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdLocalNone}, '本地未分组', -6, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdLocalNone})
                """.trimIndent()
                db.execSQL(insertBookGroupLocalNoneGroupSql)
                @Language("sql")
                val insertBookGroupErrorSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdError}, '更新失败', -1, 1
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdError})
                """.trimIndent()
                db.execSQL(insertBookGroupErrorSql)
                @Language("sql")
                val upBookSourceLoginUiSql =
                    "update book_sources set loginUi = null where loginUi = 'null'"
                db.execSQL(upBookSourceLoginUiSql)
                @Language("sql")
                val upRssSourceLoginUiSql =
                    "update rssSources set loginUi = null where loginUi = 'null'"
                db.execSQL(upRssSourceLoginUiSql)
                @Language("sql")
                val upHttpTtsLoginUiSql =
                    "update httpTTS set loginUi = null where loginUi = 'null'"
                db.execSQL(upHttpTtsLoginUiSql)
                @Language("sql")
                val upHttpTtsConcurrentRateSql =
                    "update httpTTS set concurrentRate = '0' where concurrentRate is null"
                db.execSQL(upHttpTtsConcurrentRateSql)
                db.query("select * from keyboardAssists order by serialNo").use {
                    if (it.count == 0) {
                        DefaultData.keyboardAssists.forEach { keyboardAssist ->
                            val contentValues = ContentValues().apply {
                                put("type", keyboardAssist.type)
                                put("key", keyboardAssist.key)
                                put("value", keyboardAssist.value)
                                put("serialNo", keyboardAssist.serialNo)
                            }
                            db.insert(
                                "keyboardAssists",
                                SQLiteDatabase.CONFLICT_REPLACE,
                                contentValues
                            )
                        }
                    }
                }

                // 一次性回填书源的内容类型初判与默认值（仅针对缺失项）
                // 音频类关键词命中 -> AUDIO
                db.execSQL(
                    """
                    update book_sources set contentTypeHint = 'AUDIO'
                    where (contentTypeHint is null or trim(contentTypeHint) = '')
                    and (
                        bookSourceName like '%有声%'
                        or bookSourceName like '%听书%'
                        or lower(bookSourceName) like '%audio%'
                        or lower(bookSourceName) like '%podcast%'
                        or lower(bookSourceName) like '%radio%'
                        or lower(bookSourceUrl) like '%audio%'
                        or lower(bookSourceUrl) like '%podcast%'
                        or lower(bookSourceUrl) like '%radio%'
                    )
                    """.trimIndent()
                )

                // 图片/漫画类关键词命中 -> IMAGE
                db.execSQL(
                    """
                    update book_sources set contentTypeHint = 'IMAGE'
                    where (contentTypeHint is null or trim(contentTypeHint) = '')
                    and (
                        bookSourceName like '%漫画%'
                        or bookSourceName like '%连环画%'
                        or bookSourceName like '%绘本%'
                        or lower(bookSourceName) like '%comic%'
                        or lower(bookSourceName) like '%manga%'
                        or lower(bookSourceUrl) like '%comic%'
                        or lower(bookSourceUrl) like '%manga%'
                    )
                    """.trimIndent()
                )

                // 音乐类关键词命中 -> MUSIC
                db.execSQL(
                    """
                    update book_sources set contentTypeHint = 'MUSIC'
                    where (contentTypeHint is null or trim(contentTypeHint) = '')
                    and (
                        bookSourceName like '%音乐%'
                        or lower(bookSourceName) like '%music%'
                        or lower(bookSourceName) like '%song%'
                        or lower(bookSourceName) like '%album%'
                        or lower(bookSourceName) like '%mv%'
                        or lower(bookSourceUrl) like '%music%'
                        or lower(bookSourceUrl) like '%song%'
                        or lower(bookSourceUrl) like '%album%'
                        or lower(bookSourceUrl) like '%mv%'
                    )
                    """.trimIndent()
                )

                // 短剧/视频/影视类关键词命中 -> DRAMA
                db.execSQL(
                    """
                    update book_sources set contentTypeHint = 'DRAMA'
                    where (contentTypeHint is null or trim(contentTypeHint) = '')
                    and (
                        bookSourceName like '%短剧%'
                        or lower(bookSourceName) like '%drama%'
                        or lower(bookSourceName) like '%video%'
                        or lower(bookSourceName) like '%movie%'
                        or lower(bookSourceName) like '%film%'
                        or lower(bookSourceName) like '%tv%'
                        or lower(bookSourceName) like '%vod%'
                        or lower(bookSourceName) like '%watch%'
                        or lower(bookSourceName) like '%play%'
                        or lower(bookSourceName) like '%series%'
                        or lower(bookSourceName) like '%episode%'
                        or lower(bookSourceName) like '%season%'
                        or lower(bookSourceUrl) like '%drama%'
                        or lower(bookSourceUrl) like '%video%'
                        or lower(bookSourceUrl) like '%movie%'
                        or lower(bookSourceUrl) like '%film%'
                        or lower(bookSourceUrl) like '%tv%'
                        or lower(bookSourceUrl) like '%vod%'
                        or lower(bookSourceUrl) like '%watch%'
                        or lower(bookSourceUrl) like '%play%'
                        or lower(bookSourceUrl) like '%series%'
                        or lower(bookSourceUrl) like '%episode%'
                        or lower(bookSourceUrl) like '%season%'
                    )
                    """.trimIndent()
                )

                // 文件/文档类关键词命中 -> FILE
                db.execSQL(
                    """
                    update book_sources set contentTypeHint = 'FILE'
                    where (contentTypeHint is null or trim(contentTypeHint) = '')
                    and (
                        lower(bookSourceName) like '%file%'
                        or lower(bookSourceUrl) like '%file%'
                        or lower(bookSourceUrl) like '%.pdf%'
                        or lower(bookSourceUrl) like '%.epub%'
                        or lower(bookSourceUrl) like '%.mobi%'
                        or lower(bookSourceUrl) like '%.azw%'
                        or lower(bookSourceUrl) like '%.doc%'
                        or lower(bookSourceUrl) like '%.docx%'
                        or lower(bookSourceUrl) like '%.xls%'
                        or lower(bookSourceUrl) like '%.xlsx%'
                        or lower(bookSourceUrl) like '%.ppt%'
                        or lower(bookSourceUrl) like '%.pptx%'
                    )
                    """.trimIndent()
                )

                // 其余缺失项设为 TEXT 作为默认值
                db.execSQL(
                    """
                    update book_sources set contentTypeHint = 'TEXT'
                    where contentTypeHint is null or trim(contentTypeHint) = ''
                    """.trimIndent()
                )
            }
        }

    }

}