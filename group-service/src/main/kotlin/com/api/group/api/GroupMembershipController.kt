package com.api.group.api

import com.api.group.api.dto.GroupMembershipsResponse
import com.api.group.api.dto.toResponse
import com.api.group.application.query.GetGroupMembershipsQuery
import com.api.group.application.query.GetGroupMembershipsQueryHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@Tag(name = "Internal memberships", description = "서버 간 그룹 가입 정보 조회")
class GroupMembershipController(private val handler: GetGroupMembershipsQueryHandler) {
    private val validMemberId = Regex("[A-Za-z0-9]{1,32}")

    @GetMapping("/internal/group-members", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "멤버 ID 목록으로 그룹명·권한 일괄 조회", description = "가입 정보가 없는 ID는 응답 목록에서 제외합니다.")
    fun findMemberships(
        @Parameter(description = "영문·숫자 ID를 쉼표로 구분, 1~100개", required = true, example = "ax1qwsd12s,bs8as1vfws")
        @RequestParam(name = "memberIds") memberIds: List<String>,
    ): GroupMembershipsResponse {
        if (memberIds.isEmpty() || memberIds.size > 100 || memberIds.any { !validMemberId.matches(it) }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "memberIds는 영문·숫자 1~32자의 ID를 1~100개 지정해야 합니다.")
        }
        return handler.handle(GetGroupMembershipsQuery(memberIds)).toResponse()
    }
}
