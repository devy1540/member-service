package com.api.member.api

import com.api.member.api.dto.MemberListResponse
import com.api.member.api.dto.toResponse
import com.api.member.application.query.GetMembersQuery
import com.api.member.application.query.GetMembersQueryHandler
import com.api.member.application.query.MemberNameOrder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@Tag(name = "Members", description = "멤버 조회")
class MemberController(private val handler: GetMembersQueryHandler) {

    @GetMapping("/api/members", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "멤버 목록 조회", description = "멤버 테이블을 먼저 페이징하고 그룹 서비스에서 그룹명·권한을 조회합니다. sort가 없거나 비어 있으면 정렬을 추가하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "필수 page 누락 또는 잘못된 페이징·정렬 값",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))])
    @ApiResponse(responseCode = "502", description = "그룹 서비스 호출 또는 응답 오류",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))])
    fun findMembers(
        @Parameter(description = "페이지 번호, 0부터 시작", required = true, example = "0")
        @RequestParam(name = "page") @Min(0) page: Int,
        @Parameter(description = "페이지 크기, 기본 20, 최대 100", example = "20")
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) size: Int,
        @Parameter(description = "이름 정렬. 생략 시 DB 반환 순서", schema = Schema(allowableValues = ["name,asc", "name,desc"]))
        @RequestParam(name = "sort", required = false) sort: String?,
    ): MemberListResponse {
        val order = when {
            sort.isNullOrBlank() -> null
            sort == "name,asc" -> MemberNameOrder.ASC
            sort == "name,desc" -> MemberNameOrder.DESC
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sort는 name,asc 또는 name,desc만 가능합니다.")
        }
        return handler.handle(GetMembersQuery(page, size, order)).toResponse()
    }
}
