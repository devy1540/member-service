package com.api.group.infrastructure.persistence

import com.api.group.application.command.GroupWritePort
import com.api.group.application.command.InitializeGroupsCommand
import com.api.group.application.command.InitializeGroupsResult
import com.api.group.infrastructure.persistence.table.GroupMembers
import com.api.group.infrastructure.persistence.table.Groups
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ExposedGroupWriteAdapter : GroupWritePort {
    @Transactional
    override fun initializeIfEmpty(command: InitializeGroupsCommand): InitializeGroupsResult {
        if (Groups.selectAll().count() != 0L) return InitializeGroupsResult(0, 0, skipped = true)
        command.groups.forEach { group ->
            val groupId = Groups.insert {
                group.id?.let { value -> it[id] = value }
                it[name] = group.name
                it[pricing] = group.pricing
            }[Groups.id]
            group.memberships.forEach { membership ->
                GroupMembers.insert {
                    it[GroupMembers.groupId] = groupId
                    it[memberId] = membership.memberId
                    it[permission] = membership.permission
                }
            }
        }
        return InitializeGroupsResult(command.groups.size, command.groups.sumOf { it.memberships.size }, skipped = false)
    }
}
