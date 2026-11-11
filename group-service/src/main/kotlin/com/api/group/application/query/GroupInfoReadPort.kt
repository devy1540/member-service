package com.api.group.application.query

import com.api.group.domain.Permission
import com.api.group.domain.Pricing
import java.time.OffsetDateTime

data class GroupDetailReadModel(
    val memberId: String,
    val groupId: String,
    val groupName: String,
    val registeredDate: OffsetDateTime,
    val pricing: Pricing,
    val permission: Permission,
)
data class GroupSummaryMember(val memberId: String, val permission: Permission)
data class GroupSummaryReadModel(val groupId: String, val groupName: String, val totalMemberCount: Long, val members: List<GroupSummaryMember>)
data class GroupsSummaryReadModel(val groups: List<GroupSummaryReadModel>)

interface GroupInfoReadPort {
    fun findByMemberId(memberId: String): GroupDetailReadModel?
    fun summarizeByMemberIds(memberIds: List<String>): List<GroupSummaryReadModel>
}
