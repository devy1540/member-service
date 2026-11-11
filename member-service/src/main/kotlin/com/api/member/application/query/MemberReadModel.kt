package com.api.member.application.query

import com.api.member.domain.MembershipPermission

data class MemberSummary(val id: String, val name: String)

data class MemberListItem(
    val id: String,
    val name: String,
    val group: String?,
    val permission: MembershipPermission?,
)

data class MembersReadModel(val members: List<MemberListItem>)
