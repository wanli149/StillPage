package io.stillpage.app.model

import android.content.Context
import io.stillpage.app.constant.IntentAction
import io.stillpage.app.service.DownloadService
import io.stillpage.app.utils.startService

object Download {


    fun start(context: Context, url: String, fileName: String) {
        context.startService<DownloadService> {
            action = IntentAction.start
            putExtra("url", url)
            putExtra("fileName", fileName)
        }
    }

}