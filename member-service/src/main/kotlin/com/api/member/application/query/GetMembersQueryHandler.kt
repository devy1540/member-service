package com.api.member.application.query

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class GetMembersQueryHandler(
    private val members: MemberReadPort,
    private val groups: GroupMembershipReadPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(query: GetMembersQuery): MembersReadModel {
        val started = System.nanoTime()
        var stage = "member-store"
        try {
            // The adapter finishes its read transaction before the remote call.
            val pageMembers = members.findPage(query)
            stage = "group-service"
            val memberships = if (pageMembers.isEmpty()) emptyMap()
                else groups.findByMemberIds(pageMembers.map { it.id })
            val result = MembersReadModel(pageMembers.map { member ->
                val membership = memberships[member.id]
                MemberListItem(member.id, member.name, membership?.group, membership?.permission)
            })
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            val groupedCount = result.members.count { it.group != null }
            log.atInfo()
                .addKeyValue("eventType", "member.query.completed")
                .addKeyValue("cqrs", "query").addKeyValue("operation", "GetMembers")
                .addKeyValue("page", query.page).addKeyValue("size", query.size)
                .addKeyValue("sort", query.order?.name ?: "NONE")
                .addKeyValue("returnedCount", result.members.size).addKeyValue("groupedCount", groupedCount)
                .addKeyValue("durationMs", durationMs)
                .log("GetMembers completed (page={}, size={}, sort={}, returned={}, grouped={}, durationMs={})",
                    query.page, query.size, query.order?.name ?: "NONE", result.members.size, groupedCount, durationMs)
            return result
        } catch (exception: Exception) {
            val failure = exception as? GroupMembershipLookupException
            log.atError()
                .addKeyValue("eventType", "member.query.failed")
                .addKeyValue("cqrs", "query").addKeyValue("operation", "GetMembers")
                .addKeyValue("page", query.page).addKeyValue("size", query.size)
                .addKeyValue("sort", query.order?.name ?: "NONE").addKeyValue("stage", stage)
                .addKeyValue("failureReason", failure?.reason?.name ?: "READ_FAILURE")
                .addKeyValue("upstreamStatus", failure?.upstreamStatus)
                .addKeyValue("errorType", exception.javaClass.simpleName)
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .log("GetMembers failed (stage={}, reason={})", stage, failure?.reason?.name ?: "READ_FAILURE")
            throw exception
        }
    }
}
