package com.api.group.application.command

import com.api.group.application.query.GroupMembershipReadPort
import com.api.group.domain.Permission
import com.api.group.domain.Pricing
import com.api.group.logging.LogCapture
import com.api.group.logging.fields
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
import java.sql.SQLException

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:group-command-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "logging.file.name=build/test-logs/group-command.jsonl",
])
class InitializeGroupsCommandTests @Autowired constructor(
    private val handler: InitializeGroupsCommandHandler,
    private val reads: GroupMembershipReadPort,
    dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private fun group(name: String, memberId: String) = InitialGroup(name, Pricing.Free, listOf(InitialMembership(memberId, Permission.Admin)))
    @BeforeEach fun clear() {
        jdbc.update("DELETE FROM \"group_member\"")
        jdbc.update("DELETE FROM \"group\"")
    }

    @Test
    fun `groups and memberships commit together and are queryable`() {
        val result = handler.handle(InitializeGroupsCommand(listOf(group("team1", "one"), group("team2", "two"))))
        assertEquals(InitializeGroupsResult(2, 2, false), result)
        val memberships = reads.findByMemberIds(listOf("one", "two")).associateBy { it.memberId }
        assertEquals("team1", memberships.getValue("one").group)
        assertEquals("team2", memberships.getValue("two").group)
    }

    @Test
    fun `existing groups are not overwritten`() {
        handler.handle(InitializeGroupsCommand(listOf(group("existing", "one"))))
        assertEquals(InitializeGroupsResult(0, 0, true), handler.handle(InitializeGroupsCommand(listOf(group("new", "two")))))
        assertEquals(listOf("one"), reads.findByMemberIds(listOf("one", "two")).map { it.memberId })
    }

    @Test
    fun `membership conflicts roll back all groups and memberships in the command`() {
        LogCapture(InitializeGroupsCommandHandler::class.java).use { capture ->
            val failure = assertThrows(Exception::class.java) {
                handler.handle(InitializeGroupsCommand(listOf(group("team1", "duplicate"), group("team2", "duplicate"))))
            }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is SQLException && it.sqlState == "23505" })
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM \"group\"", Long::class.java))
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM \"group_member\"", Long::class.java))
            assertEquals(listOf("group.command.failed"), capture.events.map { it.fields()["eventType"] })
        }
    }
}
