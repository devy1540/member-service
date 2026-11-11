package com.api.member.domain

import io.swagger.v3.oas.annotations.media.Schema

/** Permission values in the group-service HTTP contract. */
@Schema(enumAsRef = true)
enum class MembershipPermission {
    Viewer,
    Editor,
    Admin,
}
