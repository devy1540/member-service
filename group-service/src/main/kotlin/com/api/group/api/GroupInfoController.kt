package com.api.group.api

import com.api.group.api.dto.GroupDetailResponse
import com.api.group.api.dto.GroupsSummaryResponse
import com.api.group.api.dto.toResponse
import com.api.group.application.query.GetGroupByMemberQuery
import com.api.group.application.query.GetGroupInfoQueryHandler
import com.api.group.application.query.GetGroupsSummaryQuery
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.node.NullNode

@RestController
@Tag(name = "Groups", description = "멤버 기준 그룹 정보 조회")
class GroupInfoController(private val handler: GetGroupInfoQueryHandler) {
    private val validMemberId = Regex("[A-Za-z0-9]{1,32}")

    @GetMapping("/api/group", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "멤버의 그룹 정보 조회", description = "미가입 멤버는 200과 JSON null을 반환합니다. registeredDate는 UTC 기준 그룹 가입 시각입니다.")
    @ApiResponse(responseCode = "200", description = "그룹 가입 정보 또는 null",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = GroupDetailResponse::class))])
    fun findGroup(
        @Parameter(required = true, example = "ax1qwsd12s")
        @RequestParam(name = "memberId") @Pattern(regexp = "[A-Za-z0-9]{1,32}") memberId: String,
    ): ResponseEntity<Any> {
        val body: Any = handler.handle(GetGroupByMemberQuery(memberId))?.toResponse() ?: NullNode.instance
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)
    }

    @GetMapping("/api/groups/summary", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "멤버 배열로 그룹 요약 조회", description = "요청한 멤버가 속한 그룹을 찾고, 각 그룹의 모든 멤버를 members에 반환합니다. totalMemberCount는 members 배열 길이와 같습니다. 미가입 ID는 그룹 선택에서 제외합니다.")
    fun summarizeGroups(
        @Parameter(required = true, description = "쉼표로 구분한 영문·숫자 ID, 최대 100개", example = "ax1qwsd12s,bs8as1vfws")
        @RequestParam(name = "memberIds") memberIds: List<String>,
    ): GroupsSummaryResponse {
        if (memberIds.isEmpty() || memberIds.size > 100 || memberIds.any { !validMemberId.matches(it) }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "memberIds는 영문·숫자 1~32자의 ID를 1~100개 지정해야 합니다.")
        }
        return handler.handle(GetGroupsSummaryQuery(memberIds)).toResponse()
    }
}
