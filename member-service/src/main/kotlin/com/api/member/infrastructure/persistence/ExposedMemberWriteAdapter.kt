package com.api.member.infrastructure.persistence

import com.api.member.application.command.InitializeMembersCommand
import com.api.member.application.command.InitializeMembersResult
import com.api.member.application.command.MemberWritePort
import com.api.member.infrastructure.persistence.table.Members
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ExposedMemberWriteAdapter : MemberWritePort {
    @Transactional
    override fun initializeIfEmpty(command: InitializeMembersCommand): InitializeMembersResult {
        if (Members.selectAll().count() != 0L) return InitializeMembersResult(0, skipped = true)
        command.members.forEach { member ->
            Members.insert {
                it[id] = member.id
                it[name] = member.name
                it[email] = member.email
                it[address] = member.address
            }
        }
        return InitializeMembersResult(command.members.size, skipped = false)
    }
}
