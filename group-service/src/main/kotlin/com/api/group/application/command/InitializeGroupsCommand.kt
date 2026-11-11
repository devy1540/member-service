package com.api.group.application.command

import com.api.group.domain.Permission
import com.api.group.domain.Pricing

data class InitialMembership(val memberId: String, val permission: Permission)
data class InitialGroup(val name: String, val pricing: Pricing, val memberships: List<InitialMembership>, val id: String? = null)
data class InitializeGroupsCommand(val groups: List<InitialGroup>)
data class InitializeGroupsResult(val insertedGroups: Int, val insertedMemberships: Int, val skipped: Boolean)
