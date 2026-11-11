package com.api.group.application.query

fun interface GroupMembershipReadPort {
    fun findByMemberIds(memberIds: List<String>): List<GroupMembershipReadModel>
}
