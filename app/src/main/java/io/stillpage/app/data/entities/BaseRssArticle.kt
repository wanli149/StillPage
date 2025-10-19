package io.stillpage.app.data.entities

import io.stillpage.app.help.RuleBigDataHelp
import io.stillpage.app.model.analyzeRule.RuleDataInterface
import io.stillpage.app.utils.GSON

interface BaseRssArticle : RuleDataInterface {

    var origin: String
    var link: String

    var variable: String?

    override fun putVariable(key: String, value: String?): Boolean {
        if (super.putVariable(key, value)) {
            variable = GSON.toJson(variableMap)
        }
        return true
    }

    override fun putBigVariable(key: String, value: String?) {
        RuleBigDataHelp.putRssVariable(origin, link, key, value)
    }

    override fun getBigVariable(key: String): String? {
        return RuleBigDataHelp.getRssVariable(origin, link, key)
    }

}