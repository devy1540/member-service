package com.api.member.application.query

enum class GroupLookupFailureReason { HTTP_ERROR, TIMEOUT, CONNECTION_FAILURE, INVALID_RESPONSE }

class GroupMembershipLookupException(
    val reason: GroupLookupFailureReason,
    val upstreamStatus: Int? = null,
    cause: Throwable? = null,
) : RuntimeException("그룹 정보를 조회하지 못했습니다.", cause)
