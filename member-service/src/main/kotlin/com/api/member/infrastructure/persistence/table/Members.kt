package com.api.member.infrastructure.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.util.UUID

object Members : Table("\"member\"") {
    val id = varchar("id", 32).clientDefault { UUID.randomUUID().toString().replace("-", "") }
    val name = varchar("name", 100)
    val email = varchar("email", 254)
    val address = varchar("address", 500)
    val registeredDate = timestampWithTimeZone("\"registeredDate\"")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id, name = "pk_member")
}
