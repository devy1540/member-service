package com.api.group.application.query

import com.api.group.domain.Permission

data class GroupMembershipReadModel(val memberId: String, val group: String, val permission: Permission)
data class GroupMembershipsReadModel(val memberships: List<GroupMembershipReadModel>)
