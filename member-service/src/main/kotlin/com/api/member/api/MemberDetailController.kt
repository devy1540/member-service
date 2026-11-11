package com.api.member.api

import com.api.member.api.dto.MemberDetailResponse
import com.api.member.api.dto.toResponse
import com.api.member.application.query.GetMemberDetailQuery
import com.api.member.application.query.GetMemberDetailQueryHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Members")
class MemberDetailController(private val handler: GetMemberDetailQueryHandler) {
    @GetMapping("/api/member/{memberId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "멤버 상세 조회", description = "그룹 정보는 그룹 서비스에서 조회합니다. 날짜는 UTC 기준 yyyy-MM-dd HH:mm이며, 그룹 날짜는 멤버의 가입 시각입니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 멤버 ID",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))])
    @ApiResponse(responseCode = "404", description = "존재하지 않는 멤버",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))])
    @ApiResponse(responseCode = "502", description = "그룹 서비스 호출 또는 응답 오류",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))])
    fun findMember(
        @Parameter(description = "영문·숫자 1~32자", example = "ax1qwsd12s", required = true)
        @PathVariable(name = "memberId") @Pattern(regexp = "[A-Za-z0-9]{1,32}") memberId: String,
    ): MemberDetailResponse = handler.handle(GetMemberDetailQuery(memberId)).toResponse()
}
