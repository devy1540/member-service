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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:group-api-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "logging.file.name=build/test-logs/group-api.jsonl",
    ],
)
class GroupMembershipApiTests {
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @LocalServerPort private var port: Int = 0
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @BeforeEach
    fun reset() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            GroupMembers.deleteAll()
            Groups.deleteAll()
            val groupId = Groups.insert {
                it[name] = "test1"
                it[pricing] = Pricing.Free
            }[Groups.id]
            listOf("a1" to Permission.Editor, "b2" to Permission.Viewer).forEach { (memberId, permission) ->
                GroupMembers.insert {
                    it[GroupMembers.groupId] = groupId
                    it[GroupMembers.memberId] = memberId
                    it[GroupMembers.permission] = permission
                }
            }
        }
    }

    @Test
    fun `batch lookup joins group names and permissions and omits unknown ids`() {
        val response = get("?memberIds=b2,missing,a1")
        assertEquals(200, response.statusCode(), response.body())
        val memberships = json.readTree(response.body()).path("memberships").associateBy { it.path("memberId").asString() }
        assertEquals(setOf("a1", "b2"), memberships.keys)
        assertEquals("test1", memberships.getValue("a1").path("group").asString())
        assertEquals("Editor", memberships.getValue("a1").path("permission").asString())
        assertEquals("Viewer", memberships.getValue("b2").path("permission").asString())
    }

    @Test
    fun `unknown memberships are an empty list`() {
        val response = get("?memberIds=missing")
        assertEquals(200, response.statusCode())
        assertEquals(0, json.readTree(response.body()).path("memberships").size())
    }

    @Test
    fun `repeated ids do not duplicate the response`() {
        val response = get("?memberIds=a1,a1")
        assertEquals(200, response.statusCode())
        assertEquals(1, json.readTree(response.body()).path("memberships").size())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "?memberIds=", "?memberIds=invalid_id", "?memberIds=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"])
    fun `invalid member id batches are rejected`(query: String) {
        assertEquals(400, get(query).statusCode())
    }

    @Test
    fun `batches are limited to one hundred ids`() {
        assertEquals(400, get("?memberIds=" + (1..101).joinToString(",") { "member$it" }).statusCode())
    }

    private fun get(query: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/internal/group-members$query")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )
}
