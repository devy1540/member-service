package com.api.group.api.dto

import com.api.group.application.query.GroupDetailReadModel
import com.api.group.application.query.GroupsSummaryReadModel
import com.api.group.domain.Permission
import com.api.group.domain.Pricing
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val utcDateTime = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC)

data class GroupDetailResponse(
    @field:Schema(example = "ax1qwsd12s", requiredMode = Schema.RequiredMode.REQUIRED) val memberId: String,
    @field:Schema(example = "arutjf9348", requiredMode = Schema.RequiredMode.REQUIRED) val groupId: String,
    @field:Schema(example = "Test1", requiredMode = Schema.RequiredMode.REQUIRED) val groupName: String,
    @field:Schema(description = "해당 멤버가 그룹에 가입한 시각, UTC yyyy-MM-dd HH:mm", example = "2026-08-15 13:00",
        pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$", requiredMode = Schema.RequiredMode.REQUIRED)
    val registeredDate: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val pricing: Pricing,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val permission: Permission,
)

data class GroupSummaryMemberResponse(
    @field:Schema(example = "ax1qwsd12s", requiredMode = Schema.RequiredMode.REQUIRED) val memberId: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val permission: Permission,
)
data class GroupSummaryResponse(
    @field:Schema(example = "arutjf9348", requiredMode = Schema.RequiredMode.REQUIRED) val groupId: String,
    @field:Schema(example = "Test1", requiredMode = Schema.RequiredMode.REQUIRED) val groupName: String,
    @field:Schema(description = "그룹 전체 인원수. members 배열 길이와 동일", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    val totalMemberCount: Long,
    @field:Schema(description = "해당 그룹에 가입한 모든 멤버", requiredMode = Schema.RequiredMode.REQUIRED)
    val members: List<GroupSummaryMemberResponse>,
)
data class GroupsSummaryResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val groups: List<GroupSummaryResponse>,
)

fun GroupDetailReadModel.toResponse(): GroupDetailResponse = GroupDetailResponse(
    memberId, groupId, groupName, utcDateTime.format(registeredDate.toInstant()), pricing, permission,
)

fun GroupsSummaryReadModel.toResponse(): GroupsSummaryResponse = GroupsSummaryResponse(groups.map { group ->
    GroupSummaryResponse(group.groupId, group.groupName, group.totalMemberCount,
        group.members.map { GroupSummaryMemberResponse(it.memberId, it.permission) })
})
