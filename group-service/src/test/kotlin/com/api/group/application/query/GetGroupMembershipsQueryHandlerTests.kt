package com.api.group.application.query

import com.api.group.domain.Permission
import com.api.group.logging.LogCapture
import com.api.group.logging.fields
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GetGroupMembershipsQueryHandlerTests {
    @Test
    fun `duplicates are removed before the read port and only counts are logged`() {
        val handler = GetGroupMembershipsQueryHandler(GroupMembershipReadPort { ids ->
            assertEquals(listOf("member1", "member2"), ids)
            listOf(GroupMembershipReadModel("member1", "private-group", Permission.Viewer))
        })
        LogCapture(GetGroupMembershipsQueryHandler::class.java).use { capture ->
            val result = handler.handle(GetGroupMembershipsQuery(listOf("member1", "member2", "member1")))
            assertEquals(1, result.memberships.size)
            val event = capture.events.single()
            assertEquals("group.query.completed", event.fields()["eventType"])
            assertEquals(2, event.fields()["requestedCount"])
            assertEquals(1, event.fields()["returnedCount"])
            assertFalse((event.formattedMessage + event.fields()).contains("private-group"))
        }
    }

    @Test
    fun `read failures are rethrown and recorded`() {
        val handler = GetGroupMembershipsQueryHandler(GroupMembershipReadPort { error("private-database-error") })
        LogCapture(GetGroupMembershipsQueryHandler::class.java).use { capture ->
            assertThrows(IllegalStateException::class.java) { handler.handle(GetGroupMembershipsQuery(listOf("member1"))) }
            assertEquals("group.query.failed", capture.events.single().fields()["eventType"])
            assertNull(capture.events.single().throwableProxy)
        }
    }
}
