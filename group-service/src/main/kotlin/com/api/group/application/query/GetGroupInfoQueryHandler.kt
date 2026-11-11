package com.api.group.application.query

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

data class GetGroupByMemberQuery(val memberId: String)
data class GetGroupsSummaryQuery(val memberIds: List<String>)

@Service
class GetGroupInfoQueryHandler(private val groups: GroupInfoReadPort) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(query: GetGroupByMemberQuery): GroupDetailReadModel? = logged("GetGroupByMember", 1) {
        groups.findByMemberId(query.memberId)
    }

    fun handle(query: GetGroupsSummaryQuery): GroupsSummaryReadModel = logged("GetGroupsSummary", query.memberIds.distinct().size) {
        GroupsSummaryReadModel(groups.summarizeByMemberIds(query.memberIds.distinct()))
    }

    private fun <T> logged(operation: String, requestedCount: Int, block: () -> T): T {
        val started = System.nanoTime()
        try {
            val result = block()
            log.atInfo().addKeyValue("eventType", "group.info.query.completed")
                .addKeyValue("cqrs", "query").addKeyValue("operation", operation)
                .addKeyValue("requestedCount", requestedCount)
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .log("{} completed (requested={})", operation, requestedCount)
            return result
        } catch (exception: Exception) {
            log.atError().addKeyValue("eventType", "group.info.query.failed")
                .addKeyValue("cqrs", "query").addKeyValue("operation", operation)
                .addKeyValue("failureReason", "READ_FAILURE").addKeyValue("errorType", exception.javaClass.simpleName)
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .log("{} failed (reason=READ_FAILURE)", operation)
            throw exception
        }
    }
}
