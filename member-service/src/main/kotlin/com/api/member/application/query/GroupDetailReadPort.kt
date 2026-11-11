package com.api.member.application.query

import com.api.member.domain.GroupPricing
import com.api.member.domain.MembershipPermission
import java.time.OffsetDateTime

data class GroupDetailReadModel(
    val id: String,
    val name: String,
    val registeredDate: OffsetDateTime,
    val pricing: GroupPricing,
    val permission: MembershipPermission,
)

fun interface GroupDetailReadPort {
    fun findByMemberId(memberId: String): GroupDetailReadModel?
}
