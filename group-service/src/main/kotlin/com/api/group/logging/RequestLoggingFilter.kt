package com.api.group.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val validRequestId = Regex("[A-Za-z0-9._-]{1,64}")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val previousRequestId = MDC.get("requestId")
        val requestId = request.getHeader("X-Request-Id")
            ?.takeIf(validRequestId::matches) ?: UUID.randomUUID().toString()
        MDC.put("requestId", requestId)
        response.setHeader("X-Request-Id", requestId)
        val started = System.nanoTime()
        var failure: Exception? = null
        try {
            filterChain.doFilter(request, response)
        } catch (exception: Exception) {
            failure = exception
            throw exception
        } finally {
            try {
                val status = if (failure != null && response.status < 400) 500 else response.status
                val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                val path = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
                    ?.toString() ?: request.requestURI
                val event = when {
                    status >= 500 -> log.atError()
                    status >= 400 -> log.atWarn()
                    else -> log.atInfo()
                }
                event.addKeyValue("httpMethod", request.method)
                    .addKeyValue("requestPath", path)
                    .addKeyValue("status", status)
                    .addKeyValue("durationMs", durationMs)
                    .setCause(failure)
                    .log("HTTP {} {} -> {} ({} ms)", request.method, path, status, durationMs)
            } finally {
                if (previousRequestId == null) MDC.remove("requestId")
                else MDC.put("requestId", previousRequestId)
            }
        }
    }
}
