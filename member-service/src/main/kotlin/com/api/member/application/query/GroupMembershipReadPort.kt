package com.api.member.application.query

import com.api.member.domain.MembershipPermission

data class GroupMembershipReadModel(val memberId: String, val group: String, val permission: MembershipPermission)

fun interface GroupMembershipReadPort {
    fun findByMemberIds(memberIds: List<String>): Map<String, GroupMembershipReadModel>
}
