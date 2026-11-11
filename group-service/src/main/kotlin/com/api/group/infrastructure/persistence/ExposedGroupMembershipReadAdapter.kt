package com.api.group.infrastructure.persistence

import com.api.group.application.query.GroupMembershipReadModel
import com.api.group.application.query.GroupMembershipReadPort
import com.api.group.infrastructure.persistence.table.GroupMembers
import com.api.group.infrastructure.persistence.table.Groups
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ExposedGroupMembershipReadAdapter : GroupMembershipReadPort {
    @Transactional(readOnly = true)
    override fun findByMemberIds(memberIds: List<String>): List<GroupMembershipReadModel> =
        (GroupMembers innerJoin Groups)
            .select(GroupMembers.memberId, Groups.name, GroupMembers.permission)
            .where { GroupMembers.memberId inList memberIds }
            .map { GroupMembershipReadModel(it[GroupMembers.memberId], it[Groups.name], it[GroupMembers.permission]) }
}
