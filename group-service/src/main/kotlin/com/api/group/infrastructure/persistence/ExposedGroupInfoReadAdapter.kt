package com.api.group.infrastructure.persistence

import com.api.group.application.query.*
import com.api.group.infrastructure.persistence.table.GroupMembers
import com.api.group.infrastructure.persistence.table.Groups
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ExposedGroupInfoReadAdapter : GroupInfoReadPort {
    @Transactional(readOnly = true)
    override fun findByMemberId(memberId: String): GroupDetailReadModel? =
        (GroupMembers innerJoin Groups)
            .select(GroupMembers.memberId, Groups.id, Groups.name, GroupMembers.registeredDate, Groups.pricing, GroupMembers.permission)
            .where { GroupMembers.memberId eq memberId }.singleOrNull()?.let {
                GroupDetailReadModel(it[GroupMembers.memberId], it[Groups.id], it[Groups.name],
                    it[GroupMembers.registeredDate], it[Groups.pricing], it[GroupMembers.permission])
            }

    @Transactional(readOnly = true)
    override fun summarizeByMemberIds(memberIds: List<String>): List<GroupSummaryReadModel> {
        if (memberIds.isEmpty()) return emptyList()
        val groupIds = GroupMembers.select(GroupMembers.groupId)
            .where { GroupMembers.memberId inList memberIds }
            .map { it[GroupMembers.groupId] }.distinct()
        if (groupIds.isEmpty()) return emptyList()
        val rows = (GroupMembers innerJoin Groups)
            .select(Groups.id, Groups.name, GroupMembers.memberId, GroupMembers.permission)
            .where { GroupMembers.groupId inList groupIds }.toList()
        return rows.groupBy { it[Groups.id] }.map { (id, members) ->
            GroupSummaryReadModel(id, members.first()[Groups.name], members.size.toLong(),
                members.map { GroupSummaryMember(it[GroupMembers.memberId], it[GroupMembers.permission]) }.sortedBy { it.memberId })
        }.sortedBy { it.groupId }
    }
}
