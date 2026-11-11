package com.api.member.application.command

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class InitializeMembersCommandHandler(private val members: MemberWritePort) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(command: InitializeMembersCommand): InitializeMembersResult {
        val started = System.nanoTime()
        try {
            val result = members.initializeIfEmpty(command)
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            log.atInfo().addKeyValue("eventType", "member.command.completed")
                .addKeyValue("cqrs", "command").addKeyValue("operation", "InitializeMembers")
                .addKeyValue("insertedCount", result.insertedCount).addKeyValue("skipped", result.skipped)
                .addKeyValue("durationMs", durationMs)
                .log("InitializeMembers completed (inserted={}, skipped={}, durationMs={})", result.insertedCount, result.skipped, durationMs)
            return result
        } catch (exception: Exception) {
            log.atError().addKeyValue("eventType", "member.command.failed")
                .addKeyValue("cqrs", "command").addKeyValue("operation", "InitializeMembers")
                .addKeyValue("failureReason", "WRITE_FAILURE").addKeyValue("errorType", exception.javaClass.simpleName)
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .log("InitializeMembers failed (reason=WRITE_FAILURE)")
            throw exception
        }
    }
}
