package com.api.member.config

import org.slf4j.MDC
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

@ConfigurationProperties("clients.group-service")
data class GroupServiceProperties(
    val baseUrl: URI,
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(3),
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GroupServiceProperties::class)
class GroupServiceClientConfiguration {

    @Bean
    fun groupServiceRestClient(builder: RestClient.Builder, properties: GroupServiceProperties): RestClient {
        val httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        requestFactory.setReadTimeout(properties.readTimeout)
        return builder.baseUrl(properties.baseUrl.toString())
            .requestFactory(requestFactory)
            .requestInterceptor { request, body, execution ->
                MDC.get("requestId")?.let { request.headers.set("X-Request-Id", it) }
                execution.execute(request, body)
            }
            .build()
    }
}
