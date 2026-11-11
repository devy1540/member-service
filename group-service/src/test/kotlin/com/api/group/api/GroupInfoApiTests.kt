package com.api.group.api

import com.api.group.domain.Permission
import com.api.group.domain.Pricing
import com.api.group.infrastructure.persistence.table.GroupMembers
import com.api.group.infrastructure.persistence.table.Groups
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=jdbc:h2:mem:group-info-api-tests;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",
    "logging.file.name=build/test-logs/group-info-api.jsonl",
])
class GroupInfoApiTests {
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @LocalServerPort private var port = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @BeforeEach fun seed() {
        TransactionTemplate(transactions).executeWithoutResult {
            GroupMembers.deleteAll(); Groups.deleteAll()
            for ((id, name) in listOf("groupA" to "Test1", "groupB" to "Test2", "groupC" to "Unrelated")) {
                Groups.insert {
                    it[Groups.id] = id; it[Groups.name] = name; it[pricing] = Pricing.Premium
                    it[registeredDate] = OffsetDateTime.parse("2020-01-01T00:00:00Z")
                }
            }
            listOf(Triple("groupA", "member1", Permission.Editor), Triple("groupA", "member2", Permission.Viewer),
                Triple("groupB", "member3", Permission.Admin), Triple("groupC", "member4", Permission.Viewer)).forEach { (group, member, role) ->
                GroupMembers.insert {
                    it[groupId] = group; it[memberId] = member; it[permission] = role
                    it[registeredDate] = OffsetDateTime.parse("2026-08-15T22:00:59+09:00")
                }
            }
        }
    }

    @Test fun `single response matches image and uses UTC membership time instead of group creation time`() {
        val response = get("/api/group?memberId=member1")
        assertEquals(200, response.statusCode())
        val body = json.readTree(response.body())
        assertEquals(6, body.size())
        assertEquals("member1", body.path("memberId").asString())
        assertEquals("groupA", body.path("groupId").asString())
        assertEquals("Test1", body.path("groupName").asString())
        assertEquals("2026-08-15 13:00", body.path("registeredDate").asString())
        assertEquals("Premium", body.path("pricing").asString())
        assertEquals("Editor", body.path("permission").asString())
    }

    @Test fun `unassigned member returns JSON null rather than an empty HTTP body`() {
        val response = get("/api/group?memberId=unassigned")
        assertEquals(200, response.statusCode())
        assertEquals("null", response.body().trim())
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"))
    }

    @Test
    fun `summary selects groups by requested ids and returns every member of those groups`() {
        val response = get("/api/groups/summary?memberIds=member3,member1,unknown,member1")
        assertEquals(200, response.statusCode())
        val body = json.readTree(response.body())
        assertEquals(1, body.size())
        val groups = body.path("groups").associateBy { it.path("groupId").asString() }
        assertEquals(setOf("groupA", "groupB"), groups.keys)
        val a = groups.getValue("groupA")
        assertEquals(4, a.size())
        assertEquals("Test1", a.path("groupName").asString())
        assertEquals(2, a.path("totalMemberCount").asInt())
        assertEquals(2, a.path("members").size())
        assertEquals("member1", a.path("members")[0].path("memberId").asString())
        assertEquals("Editor", a.path("members")[0].path("permission").asString())
        assertEquals("member2", a.path("members")[1].path("memberId").asString())
        assertEquals("Viewer", a.path("members")[1].path("permission").asString())
        assertEquals(1, groups.getValue("groupB").path("totalMemberCount").asInt())
        groups.values.forEach { assertEquals(it.path("members").size(), it.path("totalMemberCount").asInt()) }
    }

    @Test fun `members from one group produce one summary object`() {
        val response = get("/api/groups/summary?memberIds=member1,member2")
        val groups = json.readTree(response.body()).path("groups")
        assertEquals(1, groups.size())
        assertEquals(2, groups[0].path("members").size())
    }

    @Test fun `summary of unassigned ids is empty`() {
        assertEquals("{\"groups\":[]}", get("/api/groups/summary?memberIds=unknown").body())
    }

    @Test fun `OpenAPI single group response accepts both the object and null`() {
        val spec = json.readTree(get("/v3/api-docs").body())
        assertFalse(spec.path("paths").has("/api/groups"))
        assertFalse(spec.path("paths").has("/api/group/summary"))
        val variants = spec.path("paths").path("/api/group").path("get").path("responses").path("200")
            .path("content").path("application/json").path("schema").path("oneOf").toList()
        assertEquals(2, variants.size)
        assertTrue(variants.any { it.path("type").asString("") == "null" })
        assertTrue(variants.any { it.path("\$ref").asString("").endsWith("/GroupDetailResponse") })
    }

    @ParameterizedTest @ValueSource(strings = ["/api/group", "/api/group?memberId=invalid_id",
        "/api/groups/summary", "/api/groups/summary?memberIds=", "/api/groups/summary?memberIds=bad_id"])
    fun `missing or invalid ids return bad request`(path: String) {
        assertEquals(400, get(path).statusCode())
    }

    private fun get(path: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofString(),
    )
}
