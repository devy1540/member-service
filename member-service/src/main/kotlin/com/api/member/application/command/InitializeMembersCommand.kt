package com.api.member.application.command

data class InitialMember(val id: String, val name: String, val email: String, val address: String)
data class InitializeMembersCommand(val members: List<InitialMember>)
data class InitializeMembersResult(val insertedCount: Int, val skipped: Boolean)
