package com.api.group.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OpenApiCustomizer
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    fun nullableGroupResponse(): OpenApiCustomizer = OpenApiCustomizer { document ->
        // OpenAPI 3.1 requires an object/null union; nullable on a root $ref is insufficient.
        document.paths?.get("/api/group")?.get?.responses?.get("200")?.content?.get("application/json")?.schema =
            ComposedSchema()
                .addOneOfItem(Schema<Any>().`$ref`("#/components/schemas/GroupDetailResponse"))
                .addOneOfItem(Schema<Any>().types(setOf("null")))
    }

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Group Service API")
                .version("1.0.0")
                .description("Group 서비스 API 명세")
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
