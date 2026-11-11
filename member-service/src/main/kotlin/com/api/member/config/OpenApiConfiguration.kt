package com.api.member.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.StringSchema
import org.springdoc.core.customizers.OpenApiCustomizer
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Member Service API")
                .version("1.0.0")
                .description("Member 서비스 API 명세")
        )
        .servers(listOf(Server().url("/").description("현재 서비스")))

    @Bean
    fun requestIdResponseHeader(): OpenApiCustomizer = OpenApiCustomizer { document ->
        document.paths?.values?.flatMap { it.readOperations() }?.forEach { operation ->
            operation.responses?.values?.forEach { response ->
                response.addHeaderObject(
                    "X-Request-Id",
                    Header()
                        .description("요청 추적 ID. 유효한 요청 헤더를 유지하거나 서버가 UUID를 생성합니다.")
                        .schema(StringSchema()),
                )
            }
        }
    }
}
