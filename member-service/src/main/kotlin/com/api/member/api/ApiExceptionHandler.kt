package com.api.member.api

import com.api.member.application.query.GroupMembershipLookupException
import com.api.member.application.query.MemberNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(MemberNotFoundException::class)
    fun memberNotFound(exception: MemberNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message!!).apply { title = "Member not found" }

    @ExceptionHandler(GroupMembershipLookupException::class)
    fun groupServiceFailure(exception: GroupMembershipLookupException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.message!!).apply {
            title = "Group service unavailable"
        }
}
