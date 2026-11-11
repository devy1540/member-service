package com.api.member.domain

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class GroupPricing { Free, Advanced, Premium }
