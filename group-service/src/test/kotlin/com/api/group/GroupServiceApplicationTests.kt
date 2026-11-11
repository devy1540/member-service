package com.api.group

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.yaml.snakeyaml.Yaml
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["logging.file.name=build/test-logs/group-service.jsonl"],
)
class GroupServiceApplicationTests {

    @LocalServerPort
    private var port: Int = 0

    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    private val json = JsonMapper.builder().build()

    @Test
    fun `OpenAPI JSON describes this service and health endpoint`() {
        val response = get("/v3/api-docs")
        assertEquals(200, response.statusCode())
        val document = json.readTree(response.body())
        assertEquals("3.1.0", document.path("openapi").asString())
        assertEquals("Group Service API", document.path("info").path("title").asString())
        assertEquals("1.0.0", document.path("info").path("version").asString())
        assertEquals("/", document.path("servers").get(0).path("url").asString())
        assertTrue(document.path("paths").path("/actuator/health").path("get").has("responses"))
    }

    @Test
    fun `OpenAPI YAML exposes the same service`() {
        val response = get("/v3/api-docs.yaml")
        assertEquals(200, response.statusCode())
        val document = Yaml().load<Map<String, Any>>(response.body())
        assertEquals("3.1.0", document["openapi"])
        assertEquals("Group Service API", (document["info"] as Map<*, *>)["title"])
    }

    @Test
    fun `H2 console is served`() {
        val response = get("/h2-console/")
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("H2 Console"))
    }

    @Test
    fun `Swagger UI is served`() {
        val response = get("/swagger-ui.html")
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Swagger UI"))
    }

    @Test
    fun `health requests preserve a valid request id`() {
        val response = get("/actuator/health", "group-test-request")
        assertEquals(200, response.statusCode())
        assertEquals("UP", json.readTree(response.body()).path("status").asString())
        assertEquals("group-test-request", response.headers().firstValue("X-Request-Id").orElseThrow())
    }

    @Test
    fun `invalid request ids are replaced with UUIDs`() {
        val response = get("/actuator/health", "invalid id")
        val id = response.headers().firstValue("X-Request-Id").orElseThrow()
        assertNotEquals("invalid id", id)
        assertEquals(id, UUID.fromString(id).toString())
    }

    private fun get(path: String, requestId: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(10))
        requestId?.let { request.header("X-Request-Id", it) }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString())
    }
}
