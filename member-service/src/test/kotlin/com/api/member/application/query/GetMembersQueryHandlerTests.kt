package com.api.member.application.query

import com.api.member.domain.MembershipPermission
import com.api.member.logging.LogCapture
import com.api.member.logging.fields
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class GetMembersQueryHandlerTests {
    @Test
    fun `read models preserve ordering and query logs expose counts without member data`() {
        val query = GetMembersQuery(1, 2, MemberNameOrder.DESC)
        val handler = GetMembersQueryHandler(
            MemberReadPort {
                assertEquals(query, it)
                listOf(MemberSummary("second", "private-name"), MemberSummary("first", "another-name"))
            },
            GroupMembershipReadPort { ids ->
                assertEquals(listOf("second", "first"), ids)
                mapOf("first" to GroupMembershipReadModel("first", "private-group", MembershipPermission.Editor))
            },
        )
        MDC.put("requestId", "cqrs-unit-query")
        try {
            LogCapture(GetMembersQueryHandler::class.java).use { capture ->
                val result = handler.handle(query)
                assertEquals(listOf("second", "first"), result.members.map { it.id })
                assertNull(result.members[0].group)
                assertNull(result.members[0].permission)
                assertEquals("private-group", result.members[1].group)
                val event = capture.events.single()
                val fields = event.fields()
                assertEquals("member.query.completed", fields["eventType"])
                assertEquals(2, fields["returnedCount"])
                assertEquals(1, fields["groupedCount"])
                assertEquals("DESC", fields["sort"])
                assertTrue((fields["durationMs"] as Long) >= 0)
                assertEquals("cqrs-unit-query", event.mdcPropertyMap["requestId"])
                assertFalse((fields.toString() + event.formattedMessage).contains("private-"))
            }
        } finally { MDC.clear() }
    }

    @Test
    fun `empty pages do not invoke the external read port`() {
        val handler = GetMembersQueryHandler(MemberReadPort { emptyList() }, GroupMembershipReadPort { error("must not call") })
        assertTrue(handler.handle(GetMembersQuery(0)).members.isEmpty())
    }

    @Test
    fun `upstream failure remains a failure and logs a sanitized reason`() {
        val failure = GroupMembershipLookupException(GroupLookupFailureReason.HTTP_ERROR, 503, RuntimeException("secret-upstream-body"))
        val handler = GetMembersQueryHandler(MemberReadPort { listOf(MemberSummary("member1", "private-name")) },
            GroupMembershipReadPort { throw failure })
        LogCapture(GetMembersQueryHandler::class.java).use { capture ->
            assertSame(failure, assertThrows(GroupMembershipLookupException::class.java) { handler.handle(GetMembersQuery(0)) })
            val event = capture.events.single()
            assertEquals("member.query.failed", event.fields()["eventType"])
            assertEquals("group-service", event.fields()["stage"])
            assertEquals("HTTP_ERROR", event.fields()["failureReason"])
            assertEquals(503, event.fields()["upstreamStatus"])
            assertFalse((event.fields().toString() + event.formattedMessage).contains("secret-upstream-body"))
            assertNull(event.throwableProxy)
        }
    }

    @Test
    fun `local read failures identify the store stage`() {
        val handler = GetMembersQueryHandler(MemberReadPort { error("private-database-error") },
            GroupMembershipReadPort { error("must not call") })
        LogCapture(GetMembersQueryHandler::class.java).use { capture ->
            assertThrows(IllegalStateException::class.java) { handler.handle(GetMembersQuery(0)) }
            assertEquals("member-store", capture.events.single().fields()["stage"])
            assertEquals("READ_FAILURE", capture.events.single().fields()["failureReason"])
            assertFalse(capture.events.single().formattedMessage.contains("private-database-error"))
        }
    }
}
