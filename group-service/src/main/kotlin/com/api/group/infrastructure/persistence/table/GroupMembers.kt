package com.api.group.infrastructure.persistence.table

import com.api.group.domain.Permission
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object GroupMembers : Table("\"group_member\"") {
    val groupId = varchar("group_id", 32).references(Groups.id, onDelete = ReferenceOption.RESTRICT)
    // Member IDs belong to the member service; there is no cross-database foreign key.
    val memberId = varchar("member_id", 32).uniqueIndex("uq_group_member_member_id")
    val registeredDate = timestampWithTimeZone("\"registeredDate\"")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val permission = enumerationByName<Permission>("permission", 16)
        .check("ck_group_member_permission") { it inList Permission.entries }

    override val primaryKey = PrimaryKey(groupId, memberId, name = "pk_group_member")
}
