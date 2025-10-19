package io.stillpage.app.utils

import io.stillpage.app.help.globalExecutor
import java.util.logging.FileHandler
import java.util.logging.LogRecord

class AsyncFileHandler(pattern: String) : FileHandler(pattern) {

    override fun publish(record: LogRecord?) {
        if (!isLoggable(record)) {
            return
        }
        globalExecutor.execute {
            super.publish(record)
        }
    }

}
