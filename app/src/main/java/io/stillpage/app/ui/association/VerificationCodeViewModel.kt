package io.stillpage.app.ui.association

import android.app.Application
import android.os.Bundle
import io.stillpage.app.base.BaseViewModel
import io.stillpage.app.constant.SourceType
import io.stillpage.app.help.source.AutoVerificationCodeHelper
import io.stillpage.app.help.source.SourceHelp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class VerificationCodeViewModel(app: Application): BaseViewModel(app) {

    var sourceOrigin = ""
    var sourceName = ""
    private var sourceType = SourceType.book

    fun initData(arguments: Bundle) {
        sourceName = arguments.getString("sourceName") ?: ""
        sourceOrigin = arguments.getString("sourceOrigin") ?: ""
        sourceType = arguments.getInt("sourceType", SourceType.book)
    }

    fun disableSource(block: () -> Unit) {
        execute {
            SourceHelp.enableSource(sourceOrigin, sourceType, false)
        }.onSuccess {
            block.invoke()
        }
    }

    fun deleteSource(block: () -> Unit) {
        execute {
            SourceHelp.deleteSource(sourceOrigin, sourceType)
        }.onSuccess {
            block.invoke()
        }
    }

    fun tryAutoRecognition(imageUrl: String, sourceOrigin: String?, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = AutoVerificationCodeHelper.recognizeVerificationCode(
                    imageUrl,
                    sourceOrigin
                )
                callback(result)
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

}
