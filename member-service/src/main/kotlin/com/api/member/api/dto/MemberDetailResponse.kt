package com.api.member.api.dto

import com.api.member.application.query.MemberDetailReadModel
import com.api.member.domain.GroupPricing
import com.api.member.domain.MembershipPermission
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val utcDateTime = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC)

data class MemberDetailResponse(
    @field:Schema(example = "ax1qwsd12s", requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
    @field:Schema(example = "user1", requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
    @field:Schema(example = "user1@email.com", requiredMode = Schema.RequiredMode.REQUIRED) val email: String,
    @field:Schema(example = "seoul, gangnamgu", requiredMode = Schema.RequiredMode.REQUIRED) val address: String,
    @field:Schema(description = "UTC 멤버 생성 시각 (yyyy-MM-dd HH:mm)", example = "2026-11-09 16:43",
        pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$", requiredMode = Schema.RequiredMode.REQUIRED)
    val registeredDate: String,
    @field:Schema(description = "소속 그룹. 미가입이면 null", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
    val group: MemberGroupDetailResponse?,
)

data class MemberGroupDetailResponse(
    @field:Schema(example = "arutjf9348", requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
    @field:Schema(example = "Test-group-01", requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
    @field:Schema(description = "UTC 그룹 가입 시각 (그룹 생성 시각이 아님)", example = "2026-11-08 11:00",
        pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$", requiredMode = Schema.RequiredMode.REQUIRED)
    val registeredDate: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val pricing: GroupPricing,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val permission: MembershipPermission,
)

fun MemberDetailReadModel.toResponse(): MemberDetailResponse = MemberDetailResponse(
    member.id, member.name, member.email, member.address, utcDateTime.format(member.registeredDate.toInstant()),
    group?.let { MemberGroupDetailResponse(it.id, it.name, utcDateTime.format(it.registeredDate.toInstant()), it.pricing, it.permission) },
)
