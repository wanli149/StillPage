package io.stillpage.app.help

import io.stillpage.app.constant.AppConst
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.DictRule
import io.stillpage.app.data.entities.HttpTTS
import io.stillpage.app.data.entities.KeyboardAssist
import io.stillpage.app.data.entities.RssSource
import io.stillpage.app.data.entities.TxtTocRule
import io.stillpage.app.help.config.LocalConfig
import io.stillpage.app.help.config.ReadBookConfig
import io.stillpage.app.help.config.ThemeConfig
import io.stillpage.app.help.coroutine.Coroutine
import io.stillpage.app.model.BookCover
import io.stillpage.app.utils.GSON
import io.stillpage.app.utils.fromJsonArray
import io.stillpage.app.utils.fromJsonObject
import io.stillpage.app.utils.printOnDebug
import splitties.init.appCtx
import java.io.File

object DefaultData {

    fun upVersion() {
        if (LocalConfig.versionCode < AppConst.appInfo.versionCode) {
            Coroutine.async {
                // 添加延迟以确保数据库完全初始化
                kotlinx.coroutines.delay(1000)

                try {
                    if (LocalConfig.needUpHttpTTS) {
                        importDefaultHttpTTS()
                    }
                    if (LocalConfig.needUpTxtTocRule) {
                        importDefaultTocRules()
                    }
                    if (LocalConfig.needUpRssSources) {
                        importDefaultRssSources()
                    }
                    if (LocalConfig.needUpDictRule) {
                        importDefaultDictRules()
                    }
                } catch (e: Exception) {
                    // 如果数据库仍然锁定，稍后重试
                    if (e.message?.contains("database is locked") == true) {
                        kotlinx.coroutines.delay(2000)
                        try {
                            if (LocalConfig.needUpHttpTTS) {
                                importDefaultHttpTTS()
                            }
                            if (LocalConfig.needUpTxtTocRule) {
                                importDefaultTocRules()
                            }
                            if (LocalConfig.needUpRssSources) {
                                importDefaultRssSources()
                            }
                            if (LocalConfig.needUpDictRule) {
                                importDefaultDictRules()
                            }
                        } catch (retryException: Exception) {
                            retryException.printOnDebug()
                        }
                    } else {
                        throw e
                    }
                }
            }.onError {
                it.printOnDebug()
            }
        }
    }

    val httpTTS: List<HttpTTS> by lazy {
        val json =
            String(
                appCtx.assets.open("defaultData${File.separator}httpTTS.json")
                    .readBytes()
            )
        HttpTTS.fromJsonArray(json).getOrElse {
            emptyList()
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?: emptyList()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}txtTocRule.json")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val themeConfigs: List<ThemeConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ThemeConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ThemeConfig.Config>(json).getOrNull() ?: emptyList()
    }

    val rssSources: List<RssSource> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json).getOrDefault(emptyList())
    }

    val coverRule: BookCover.CoverRule by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}coverRule.json")
                .readBytes()
        )
        GSON.fromJsonObject<BookCover.CoverRule>(json).getOrThrow()
    }

    val dictRules: List<DictRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}dictRules.json")
                .readBytes()
        )
        GSON.fromJsonArray<DictRule>(json).getOrThrow()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}keyboardAssists.json")
                .readBytes()
        )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    fun importDefaultHttpTTS() {
        appDb.httpTTSDao.deleteDefault()
        appDb.httpTTSDao.insert(*httpTTS.toTypedArray())
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }

    fun importDefaultRssSources() {
        appDb.rssSourceDao.deleteDefault()
        appDb.rssSourceDao.insert(*rssSources.toTypedArray())
    }

    fun importDefaultDictRules() {
        appDb.dictRuleDao.insert(*dictRules.toTypedArray())
    }

}