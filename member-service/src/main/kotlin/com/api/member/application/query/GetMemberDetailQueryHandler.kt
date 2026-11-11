package com.api.member.application.query

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class GetMemberDetailQueryHandler(private val members: MemberDetailReadPort, private val groups: GroupDetailReadPort) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(query: GetMemberDetailQuery): MemberDetailReadModel {
        val started = System.nanoTime()
        var stage = "member-store"
        try {
            val member = members.findById(query.memberId) ?: throw MemberNotFoundException()
            stage = "group-service"
            val group = groups.findByMemberId(query.memberId)
            val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            log.atInfo().addKeyValue("eventType", "member.detail.query.completed")
                .addKeyValue("cqrs", "query").addKeyValue("operation", "GetMemberDetail")
                .addKeyValue("hasGroup", group != null).addKeyValue("durationMs", duration)
                .log("GetMemberDetail completed (hasGroup={}, durationMs={})", group != null, duration)
            return MemberDetailReadModel(member, group)
        } catch (exception: Exception) {
            val failure = exception as? GroupMembershipLookupException
            val reason = if (exception is MemberNotFoundException) "NOT_FOUND" else failure?.reason?.name ?: "READ_FAILURE"
            val event = if (exception is MemberNotFoundException) log.atWarn() else log.atError()
            event.addKeyValue("eventType", "member.detail.query.failed")
                .addKeyValue("cqrs", "query").addKeyValue("operation", "GetMemberDetail")
                .addKeyValue("stage", stage).addKeyValue("failureReason", reason)
                .addKeyValue("upstreamStatus", failure?.upstreamStatus)
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .log("GetMemberDetail failed (stage={}, reason={})", stage, reason)
            throw exception
        }
    }
}
