package io.stillpage.app.exception

import io.stillpage.app.R
import splitties.init.appCtx

class NoBooksDirException: NoStackTraceException(appCtx.getString(R.string.no_books_dir))