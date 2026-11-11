package com.api.member.application.command

import com.api.member.application.query.GetMembersQuery
import com.api.member.application.query.MemberReadPort
import com.api.member.logging.LogCapture
import com.api.member.logging.fields
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
import java.sql.SQLException

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:member-command-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "logging.file.name=build/test-logs/member-command.jsonl",
])
class InitializeMembersCommandTests @Autowired constructor(
    private val handler: InitializeMembersCommandHandler,
    private val reads: MemberReadPort,
    dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private fun member(id: String) = InitialMember(id, "private-name", "$id@example.test", "private-address")
    @BeforeEach fun clear() { jdbc.update("DELETE FROM \"member\"") }

    @Test
    fun `a committed command is visible through the independent read port`() {
        LogCapture(InitializeMembersCommandHandler::class.java).use { capture ->
            val result = handler.handle(InitializeMembersCommand(listOf(member("one"), member("two"))))
            assertEquals(InitializeMembersResult(2, false), result)
            assertEquals(setOf("one", "two"), reads.findPage(GetMembersQuery(0)).map { it.id }.toSet())
            assertEquals("member.command.completed", capture.events.single().fields()["eventType"])
            assertFalse((capture.events.single().fields().toString() + capture.events.single().formattedMessage).contains("private-"))
        }
    }

    @Test
    fun `initialization preserves existing members`() {
        handler.handle(InitializeMembersCommand(listOf(member("existing"))))
        val result = handler.handle(InitializeMembersCommand(listOf(member("new"))))
        assertEquals(InitializeMembersResult(0, true), result)
        assertEquals(listOf("existing"), reads.findPage(GetMembersQuery(0)).map { it.id })
    }

    @Test
    fun `a partially failing write rolls back the entire command without success logging`() {
        LogCapture(InitializeMembersCommandHandler::class.java).use { capture ->
            val failure = assertThrows(Exception::class.java) {
                handler.handle(InitializeMembersCommand(listOf(member("duplicate"), member("duplicate"))))
            }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is SQLException && it.sqlState == "23505" })
            assertTrue(reads.findPage(GetMembersQuery(0)).isEmpty())
            assertEquals(listOf("member.command.failed"), capture.events.map { it.fields()["eventType"] })
        }
    }
}
