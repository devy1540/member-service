package com.api.group.api.dto

import com.api.group.domain.Permission
import com.api.group.application.query.GroupMembershipsReadModel
import io.swagger.v3.oas.annotations.media.Schema

data class GroupMembershipsResponse(val memberships: List<GroupMembershipResponse>)

data class GroupMembershipResponse(
    @field:Schema(example = "ax1qwsd12s", maxLength = 32)
    val memberId: String,
    @field:Schema(description = "그룹명", example = "test1")
    val group: String,
    val permission: Permission,
)

fun GroupMembershipsReadModel.toResponse(): GroupMembershipsResponse = GroupMembershipsResponse(memberships.map {
    GroupMembershipResponse(it.memberId, it.group, it.permission)
})
