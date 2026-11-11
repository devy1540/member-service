package com.api.member.application.query

fun interface MemberReadPort {
    fun findPage(query: GetMembersQuery): List<MemberSummary>
}
