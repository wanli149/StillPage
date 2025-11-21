package io.stillpage.app.ui.rss.article

import android.content.Context
import androidx.viewbinding.ViewBinding
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.data.entities.RssArticle


abstract class BaseRssArticlesAdapter<VB : ViewBinding>(context: Context, val callBack: CallBack) :
    RecyclerAdapter<RssArticle, VB>(context) {

    interface CallBack {
        val isGridLayout: Boolean
        fun readRss(rssArticle: RssArticle)
    }
}