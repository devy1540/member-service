package com.api.member.api

import com.sun.net.httpserver.HttpServer
import com.api.member.infrastructure.client.GroupMembershipHttpAdapter
import com.api.member.logging.LogCapture
import com.api.member.logging.fields
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:member-api-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "logging.file.name=build/test-logs/member-api.jsonl",
        "clients.group-service.read-timeout=500ms",
    ],
)
class MemberListApiTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @LocalServerPort private var port: Int = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private lateinit var lookupLogs: LogCapture

    @BeforeEach
    fun reset() {
        lookupLogs = LogCapture(GroupMembershipHttpAdapter::class.java)
        calls.clear()
        upstreamStatus = 200
        upstreamBody = null
        delayMillis = 0
        jdbc.update("DELETE FROM \"member\"")
        listOf("a1" to "Charlie", "b2" to "Alice", "c3" to "Bob", "d4" to "Alice", "e5" to "Dana")
            .forEach { (id, name) -> insert(id, name) }
    }

    @AfterEach fun releaseLogs() { lookupLogs.close() }

    @Test
    fun `name sorting and pagination happen before one batch group lookup`() {
        val response = get("?page=1&size=2&sort=name,asc")
        assertEquals(200, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"))
        val root = json.readTree(response.body())
        val members = root.path("members")
        assertEquals(1, root.size())
        assertEquals(listOf("c3", "a1"), members.toList().map { it.path("id").asString() })
        assertTrue(members[0].has("group") && members[0].path("group").isNull)
        assertTrue(members[0].has("permission") && members[0].path("permission").isNull)
        assertEquals("team-a1", members[1].path("group").asString())
        assertEquals("Editor", members[1].path("permission").asString())
        assertTrue(members.all { it.size() == 4 && !it.has("email") && !it.has("address") })
        assertEquals(listOf("c3", "a1"), calls.single().ids)
        val lookup = lookupLogs.events.single()
        assertEquals("group.lookup.completed", lookup.fields()["eventType"])
        assertEquals(2, lookup.fields()["requestedCount"])
        assertEquals(1, lookup.fields()["returnedCount"])
        assertEquals("member-api-test", lookup.mdcPropertyMap["requestId"])
    }

    @Test
    fun `descending name order survives a differently ordered group response`() {
        val members = members(get("?page=0&size=3&sort=name,desc"))
        assertEquals(listOf("e5", "a1", "c3"), members.toList().map { it.path("id").asString() })
        assertEquals(listOf("team-e5", "team-a1"), members.take(2).map { it.path("group").asString() })
    }

    @Test
    fun `duplicate names use member id as a stable tiebreaker`() {
        val first = members(get("?page=0&size=1&sort=name,asc"))
        val second = members(get("?page=1&size=1&sort=name,asc"))
        assertEquals("b2", first.single().path("id").asString())
        assertEquals("d4", second.single().path("id").asString())
    }

    @Test
    fun `whole group responses only enrich members in the requested page`() {
        upstreamBody = """{"groups":[{"groupId":"g1","groupName":"shared","totalMemberCount":3,"members":[{"memberId":"a1","permission":"Admin"},{"memberId":"b2","permission":"Editor"},{"memberId":"d4","permission":"Viewer"}]}]}"""
        val page = members(get("?page=0&size=1&sort=name,asc"))
        assertEquals(1, page.size())
        assertEquals("b2", page.single().path("id").asString())
        assertEquals("shared", page.single().path("group").asString())
        assertEquals("Editor", page.single().path("permission").asString())
        assertEquals(listOf("b2"), calls.single().ids)
        assertEquals(1, lookupLogs.events.single().fields()["returnedCount"])
        assertEquals(3, lookupLogs.events.single().fields()["receivedMemberCount"])
    }

    @Test
    fun `omitted and empty sort preserve the database result order`() {
        val expected = jdbc.queryForList("SELECT ID FROM \"member\" LIMIT 3 OFFSET 0", String::class.java)
        for (sort in listOf("", "&sort=")) {
            assertEquals(expected, members(get("?page=0&size=3$sort")).toList().map { it.path("id").asString() })
        }
    }

    @Test
    fun `optional size defaults to twenty`() {
        (6..25).forEach { insert("id$it", "User$it") }
        assertEquals(20, members(get("?page=0")).size())
        assertEquals(20, calls.single().ids.size)
    }

    @Test
    fun `an empty or very large page does not call the group service`() {
        upstreamStatus = 503
        assertEquals(0, members(get("?page=2147483647&size=100")).size())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `request id is propagated to the group service`() {
        assertEquals(200, get("?page=0&size=2", "member-list-correlation").statusCode())
        assertEquals("member-list-correlation", calls.single().requestId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "?size=2", "?page=-1", "?page=abc", "?page=2147483648",
        "?page=0&size=0", "?page=0&size=-1", "?page=0&size=101", "?page=0&size=abc",
        "?page=0&sort=id,asc", "?page=0&sort=name,wrong"])
    fun `invalid requests fail before any group call`(query: String) {
        val response = get(query)
        assertEquals(400, response.statusCode(), response.body())
        assertTrue(calls.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(ints = [404, 500, 503])
    fun `upstream HTTP errors are not mistaken for missing memberships`(status: Int) {
        upstreamStatus = status
        upstreamBody = "private-upstream-error"
        val response = get("?page=0&size=2")
        assertEquals(502, response.statusCode(), response.body())
        assertFalse(response.body().contains("private-upstream-error"))
        assertLookupFailure("HTTP_ERROR", status)
        assertFalse(lookupLogs.events.single().formattedMessage.contains("private-upstream-error"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-json", "{}", "{\"groups\":null}", "{\"groups\":[null]}",
        "{\"groups\":[{\"groupId\":\"g1\",\"groupName\":\"team\",\"totalMemberCount\":1,\"members\":[{\"memberId\":\"foreignId\",\"permission\":\"Editor\"}]}]}",
        "{\"groups\":[{\"groupId\":\"g1\",\"groupName\":\"team\",\"totalMemberCount\":1,\"members\":[{\"memberId\":\"a1\",\"permission\":\"Owner\"}]}]}",
        "{\"groups\":[{\"groupId\":\"g1\",\"groupName\":\"team\",\"totalMemberCount\":-1,\"members\":[]}]}",
        "{\"groups\":[{\"groupId\":\"g1\",\"groupName\":\"team\",\"totalMemberCount\":1,\"members\":[null]}]}" ])
    fun `malformed upstream responses fail explicitly`(body: String) {
        upstreamBody = body
        assertEquals(502, get("?page=0&size=5").statusCode())
        assertLookupFailure("INVALID_RESPONSE")
    }

    @Test
    fun `duplicate upstream memberships fail instead of selecting one`() {
        upstreamBody = """{"groups":[{"groupId":"g1","groupName":"team1","totalMemberCount":1,"members":[{"memberId":"a1","permission":"Editor"}]},{"groupId":"g2","groupName":"team2","totalMemberCount":1,"members":[{"memberId":"a1","permission":"Viewer"}]}]}"""
        assertEquals(502, get("?page=0&size=5").statusCode())
    }

    @Test
    fun `upstream timeout returns bad gateway`() {
        delayMillis = 1500
        assertEquals(502, get("?page=0&size=1").statusCode())
        assertLookupFailure("TIMEOUT")
    }

    @Test
    fun `OpenAPI marks page required and size optional`() {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/v3/api-docs")).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val spec = json.readTree(response.body())
        val operation = spec.path("paths").path("/api/members").path("get")
        val parameters = operation.path("parameters").associateBy { it.path("name").asString() }
        assertTrue(parameters.getValue("page").path("required").asBoolean())
        assertFalse(parameters.getValue("size").path("required").asBoolean())
        assertEquals(20, parameters.getValue("size").path("schema").path("default").asInt())
        assertTrue(operation.path("responses").path("200").path("content").has("application/json"))
        assertTrue(operation.path("responses").has("400"))
        assertTrue(operation.path("responses").has("502"))
        val memberSchema = spec.path("components").path("schemas").path("MemberResponse")
        assertEquals(setOf("id", "name", "group", "permission"),
            memberSchema.path("required").toList().map { it.asString() }.toSet())
        val permissions = memberSchema.path("properties").path("permission").path("oneOf").toList()
        assertTrue(permissions.any { it.path("type").asString("") == "null" })
        assertTrue(permissions.any { it.path("\$ref").asString("").endsWith("/MembershipPermission") })
    }

    private fun insert(id: String, name: String) {
        jdbc.update("INSERT INTO \"member\" (ID, \"name\", EMAIL, ADDRESS) VALUES (?, ?, ?, ?)",
            id, name, "$id@example.test", "서울특별시")
    }

    private fun assertLookupFailure(reason: String, status: Int? = null) {
        val event = lookupLogs.events.single()
        assertEquals("group.lookup.failed", event.fields()["eventType"])
        assertEquals(reason, event.fields()["failureReason"])
        assertEquals(status, event.fields()["upstreamStatus"])
        assertEquals("member-api-test", event.mdcPropertyMap["requestId"])
        assertNull(event.throwableProxy)
    }

    private fun get(query: String, requestId: String = "member-api-test"): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/members$query"))
            .timeout(Duration.ofSeconds(5)).header("X-Request-Id", requestId).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun members(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body()).path("members")
    }

    companion object {
        private data class Call(val ids: List<String>, val requestId: String?)
        private val calls = CopyOnWriteArrayList<Call>()
        @Volatile private var upstreamStatus = 200
        @Volatile private var upstreamBody: String? = null
        @Volatile private var delayMillis = 0L
        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = MemberListApiTests.executor
            createContext("/api/groups/summary") { exchange ->
                val ids = URLDecoder.decode(exchange.requestURI.rawQuery.substringAfter("memberIds="), StandardCharsets.UTF_8).split(",")
                calls.add(Call(ids, exchange.requestHeaders.getFirst("X-Request-Id")))
                val status = upstreamStatus
                val body = upstreamBody ?: ids.reversed().filter { it != "c3" }.joinToString(",", "{\"groups\":[", "]}") {
                    """{"groupId":"group$it","groupName":"team-$it","totalMemberCount":1,"members":[{"memberId":"$it","permission":"Editor"}]}"""
                }
                val delay = delayMillis
                try {
                    if (delay > 0) Thread.sleep(delay)
                    val bytes = body.toByteArray()
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } catch (_: java.io.IOException) {
                    // The timeout scenario intentionally closes the client connection first.
                } finally {
                    exchange.close()
                }
            }
            start()
        }

        @JvmStatic @DynamicPropertySource
        fun groupService(registry: DynamicPropertyRegistry) {
            registry.add("clients.group-service.base-url") { "http://127.0.0.1:${upstream.address.port}" }
        }

        @JvmStatic @AfterAll
        fun stopServer() {
            upstream.stop(0)
            executor.shutdownNow()
        }
    }
}
