package com.api.member.application.query

data class GetMemberDetailQuery(val memberId: String)
data class MemberDetailReadModel(val member: MemberDetailsRecord, val group: GroupDetailReadModel?)
class MemberNotFoundException : RuntimeException("멤버를 찾을 수 없습니다.")
