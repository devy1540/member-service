package com.api.group.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

class LogCapture(type: Class<*>) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(type) as Logger
    private val appender = object : ListAppender<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) {
            event.prepareForDeferredProcessing()
            super.append(event)
        }
    }.apply { context = logger.loggerContext; start() }

    init { logger.addAppender(appender) }
    val events: List<ILoggingEvent> get() = appender.list
    override fun close() { logger.detachAppender(appender); appender.stop() }
}

fun ILoggingEvent.fields(): Map<String, Any?> = keyValuePairs.orEmpty().associate { it.key to it.value }
