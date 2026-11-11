package com.api.group.application.command

fun interface GroupWritePort {
    /** Groups and memberships are initialized in a single transaction. */
    fun initializeIfEmpty(command: InitializeGroupsCommand): InitializeGroupsResult
}
