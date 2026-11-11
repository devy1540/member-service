package com.api.group.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class RequestLoggingFilterTests {

    @Test
    fun `failed requests log 500 without query or body and restore MDC`() {
        val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger
        val appender = object : ListAppender<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                event.prepareForDeferredProcessing()
                super.append(event)
            }
        }
        appender.start()
        logger.addAppender(appender)
        MDC.put("requestId", "prior-context")
        try {
            val request = MockHttpServletRequest("GET", "/api/items/42")
            request.addHeader("X-Request-Id", "failed-request")
            request.queryString = "token=private-query"
            request.setContent("private-body".toByteArray())
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/items/{id}")
            val response = MockHttpServletResponse()
            assertThrows(IllegalStateException::class.java) {
                RequestLoggingFilter().doFilter(request, response, FilterChain { _, _ ->
                    throw IllegalStateException("failure")
                })
            }
            val event = appender.list.single()
            val fields = event.keyValuePairs.associate { it.key to it.value }
            assertEquals(500, fields["status"])
            assertEquals("/api/items/{id}", fields["requestPath"])
            assertEquals("failed-request", event.mdcPropertyMap["requestId"])
            assertFalse(event.formattedMessage.contains("private-query"))
            assertFalse(event.formattedMessage.contains("private-body"))
            assertEquals("prior-context", MDC.get("requestId"))
        } finally {
            MDC.clear()
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `request MDC does not leak to the next request`() {
        MDC.clear()
        val request = MockHttpServletRequest("GET", "/actuator/health")
        val response = MockHttpServletResponse()
        RequestLoggingFilter().doFilter(request, response, FilterChain { _, _ -> })
        assertNull(MDC.get("requestId"))
    }
}
