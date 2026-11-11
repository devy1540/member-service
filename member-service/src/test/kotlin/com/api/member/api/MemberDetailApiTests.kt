package com.api.member.api

import com.api.member.application.query.GetMemberDetailQueryHandler
import com.api.member.logging.LogCapture
import com.api.member.logging.fields
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=jdbc:h2:mem:member-detail-api-tests;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",
    "logging.file.name=build/test-logs/member-detail-api.jsonl",
    "clients.group-service.read-timeout=500ms",
])
class MemberDetailApiTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @LocalServerPort private var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @BeforeEach fun seed() {
        calls.clear(); status = 200; body = validBody; delay = 0
        jdbc.update("DELETE FROM \"member\"")
        jdbc.update("INSERT INTO \"member\" (ID, \"name\", EMAIL, ADDRESS, \"registeredDate\") VALUES (?, ?, ?, ?, ?)",
            "memberA", "user1", "private@email.com", "private-address", OffsetDateTime.parse("2026-11-10T01:43:59+09:00"))
    }

    @Test fun `detail returns the exact DTO with UTC minutes and upstream group membership date`() {
        LogCapture(GetMemberDetailQueryHandler::class.java).use { logs ->
            val response = get("memberA")
            assertEquals(200, response.statusCode(), response.body())
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"))
            val detail = json.readTree(response.body())
            assertEquals(6, detail.size())
            assertEquals("memberA", detail.path("id").asString())
            assertEquals("user1", detail.path("name").asString())
            assertEquals("private@email.com", detail.path("email").asString())
            assertEquals("private-address", detail.path("address").asString())
            assertEquals("2026-11-09 16:43", detail.path("registeredDate").asString())
            val group = detail.path("group")
            assertEquals(5, group.size())
            assertEquals("groupA", group.path("id").asString())
            assertEquals("Test-group-01", group.path("name").asString())
            assertEquals("2026-11-08 11:00", group.path("registeredDate").asString())
            assertEquals("Premium", group.path("pricing").asString())
            assertEquals("Editor", group.path("permission").asString())
            assertEquals(listOf("memberId=memberA" to "detail-test"), calls.toList())
            val event = logs.events.single()
            assertEquals("member.detail.query.completed", event.fields()["eventType"])
            assertFalse((event.fields().toString() + event.formattedMessage).contains("private"))
        }
    }

    @Test fun `unassigned member retains an explicit null group`() {
        body = "null"
        val response = get("memberA")
        assertEquals(200, response.statusCode(), response.body())
        val detail = json.readTree(response.body())
        assertTrue(detail.has("group") && detail.path("group").isNull)
    }

    @Test fun `unknown member returns 404 without calling group service`() {
        assertEquals(404, get("missing").statusCode())
        assertTrue(calls.isEmpty())
    }

    @ParameterizedTest @ValueSource(strings = ["bad_id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bad%20id"])
    fun `invalid member ids return 400 before group lookup`(id: String) {
        assertEquals(400, get(id).statusCode())
        assertTrue(calls.isEmpty())
    }

    @ParameterizedTest @ValueSource(ints = [404, 500, 503])
    fun `upstream errors never masquerade as a member not found or a null group`(code: Int) {
        status = code; body = "private-upstream-error"
        val response = get("memberA")
        assertEquals(502, response.statusCode())
        assertFalse(response.body().contains("private-upstream-error"))
    }

    @ParameterizedTest @ValueSource(strings = ["", "not-json", "{}", "[]"])
    fun `empty or malformed upstream responses are rejected`(invalid: String) {
        body = invalid
        assertEquals(502, get("memberA").statusCode())
    }

    @ParameterizedTest @ValueSource(strings = ["foreignMember", "invalidDate", "oldPricing", "invalidPermission"])
    fun `upstream identity date and enums are validated`(scenario: String) {
        body = when (scenario) {
            "foreignMember" -> validBody.replace("memberA", "memberB")
            "invalidDate" -> validBody.replace("2026-11-08 11:00", "2026-02-30 11:00")
            "oldPricing" -> validBody.replace("Premium", "Premiun")
            else -> validBody.replace("Editor", "Owner")
        }
        assertEquals(502, get("memberA").statusCode())
    }

    @Test fun `upstream timeouts return 502`() {
        delay = 1500
        assertEquals(502, get("memberA").statusCode())
    }

    private fun get(id: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/member/$id"))
            .timeout(Duration.ofSeconds(5)).header("X-Request-Id", "detail-test").GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    companion object {
        private const val validBody = """{"memberId":"memberA","groupId":"groupA","groupName":"Test-group-01","registeredDate":"2026-11-08 11:00","pricing":"Premium","permission":"Editor"}"""
        private val calls = CopyOnWriteArrayList<Pair<String, String?>>()
        @Volatile private var status = 200
        @Volatile private var body = validBody
        @Volatile private var delay = 0L
        private val workers = Executors.newVirtualThreadPerTaskExecutor()
        private val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = workers
            createContext("/api/group") { exchange ->
                calls.add(exchange.requestURI.rawQuery to exchange.requestHeaders.getFirst("X-Request-Id"))
                val response = body.toByteArray(); val responseCode = status; val wait = delay
                try {
                    if (wait > 0) Thread.sleep(wait)
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(responseCode, if (response.isEmpty()) -1 else response.size.toLong())
                    if (response.isNotEmpty()) exchange.responseBody.use { it.write(response) }
                } catch (_: java.io.IOException) {
                    // A timeout test closes the connection before the delayed response.
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally { exchange.close() }
            }
            start()
        }

        @JvmStatic @DynamicPropertySource fun upstreamUrl(registry: DynamicPropertyRegistry) {
            registry.add("clients.group-service.base-url") { "http://127.0.0.1:${upstream.address.port}" }
        }
        @JvmStatic @AfterAll fun stop() { upstream.stop(0); workers.shutdownNow() }
    }
}
