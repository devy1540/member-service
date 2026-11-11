package com.api.member.application.command

fun interface MemberWritePort {
    /** Atomically initializes an empty member store; existing data is preserved. */
    fun initializeIfEmpty(command: InitializeMembersCommand): InitializeMembersResult
}
