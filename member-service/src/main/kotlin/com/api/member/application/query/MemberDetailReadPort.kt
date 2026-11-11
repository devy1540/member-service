package com.api.member.application.query

import java.time.OffsetDateTime

data class MemberDetailsRecord(
    val id: String,
    val name: String,
    val email: String,
    val address: String,
    val registeredDate: OffsetDateTime,
)

fun interface MemberDetailReadPort {
    fun findById(memberId: String): MemberDetailsRecord?
}
