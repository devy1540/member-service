package com.api.member.api.dto

import com.api.member.domain.MembershipPermission
import com.api.member.application.query.MembersReadModel
import io.swagger.v3.oas.annotations.media.Schema

data class MemberListResponse(
    @field:Schema(description = "현재 페이지의 멤버 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    val members: List<MemberResponse>,
)

data class MemberResponse(
    @field:Schema(description = "영문·숫자 멤버 ID", example = "ax1qwsd12s", maxLength = 32,
        requiredMode = Schema.RequiredMode.REQUIRED)
    val id: String,
    @field:Schema(example = "user1", requiredMode = Schema.RequiredMode.REQUIRED)
    val name: String,
    @field:Schema(description = "소속 그룹명. 미가입이면 null", example = "test1", nullable = true,
        requiredMode = Schema.RequiredMode.REQUIRED)
    val group: String?,
    @field:Schema(description = "그룹 권한. 미가입이면 null", example = "Editor",
        requiredMode = Schema.RequiredMode.REQUIRED)
    val permission: MembershipPermission?,
)

fun MembersReadModel.toResponse(): MemberListResponse = MemberListResponse(members.map {
    MemberResponse(it.id, it.name, it.group, it.permission)
})
