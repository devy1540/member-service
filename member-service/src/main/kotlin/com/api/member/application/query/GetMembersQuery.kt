package com.api.member.application.query

enum class MemberNameOrder { ASC, DESC }

data class GetMembersQuery(val page: Int, val size: Int = 20, val order: MemberNameOrder? = null)
