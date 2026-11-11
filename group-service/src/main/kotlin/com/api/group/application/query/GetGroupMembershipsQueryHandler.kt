package com.api.group.application.query

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class GetGroupMembershipsQueryHandler(private val memberships: GroupMembershipReadPort) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(query: GetGroupMembershipsQuery): GroupMembershipsReadModel {
        val started = System.nanoTime()
        val ids = query.memberIds.distinct()
        try {
            val result = GroupMembershipsReadModel(if (ids.isEmpty()) emptyList() else memberships.findByMemberIds(ids))
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            log.atInfo().addKeyValue("eventType", "group.query.completed")
                .addKeyValue("cqrs", "query").addKeyValue("operation", "GetGroupMemberships")
                .addKeyValue("requestedCount", ids.size).addKeyValue("returnedCount", result.memberships.size)
                .addKeyValue("durationMs", durationMs)
                .log("GetGroupMemberships completed (requested={}, returned={}, durationMs={})", ids.size, result.memberships.size, durationMs)
            return result
        } catch (exception: Exception) {
            log.atError().addKeyValue("eventType", "group.query.failed")
                .addKeyValue("cqrs", "query").addKeyValue("operation", "GetGroupMemberships")
                .addKeyValue("requestedCount", ids.size).addKeyValue("errorType", exception.javaClass.simpleName)
                .addKeyValue("failureReason", "READ_FAILURE")
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .log("GetGroupMemberships failed (reason=READ_FAILURE)")
            throw exception
        }
    }
}
