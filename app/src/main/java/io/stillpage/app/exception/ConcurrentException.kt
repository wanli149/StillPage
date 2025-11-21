@file:Suppress("unused")

package io.stillpage.app.exception

/**
 * 并发限制
 */
class ConcurrentException(msg: String, val waitTime: Int) : NoStackTraceException(msg)