package com.api.group.application.command

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class InitializeGroupsCommandHandler(private val groups: GroupWritePort) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(command: InitializeGroupsCommand): InitializeGroupsResult {
        val started = System.nanoTime()
        try {
            val result = groups.initializeIfEmpty(command)
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            log.atInfo().addKeyValue("eventType", "group.command.completed")
                .addKeyValue("cqrs", "command").addKeyValue("operation", "InitializeGroups")
                .addKeyValue("insertedGroups", result.insertedGroups).addKeyValue("insertedMemberships", result.insertedMemberships)
                .addKeyValue("skipped", result.skipped).addKeyValue("durationMs", durationMs)
                .log("InitializeGroups completed (groups={}, memberships={}, skipped={}, durationMs={})",
                    result.insertedGroups, result.insertedMemberships, result.skipped, durationMs)
            return result
        } catch (exception: Exception) {
            log.atError().addKeyValue("eventType", "group.command.failed")
                .addKeyValue("cqrs", "command").addKeyValue("operation", "InitializeGroups")
                .addKeyValue("failureReason", "WRITE_FAILURE").addKeyValue("errorType", exception.javaClass.simpleName)
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .log("InitializeGroups failed (reason=WRITE_FAILURE)")
            throw exception
        }
    }
}
