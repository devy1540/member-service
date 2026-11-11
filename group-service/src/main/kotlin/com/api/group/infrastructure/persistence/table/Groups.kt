package com.api.group.infrastructure.persistence.table

import com.api.group.domain.Pricing
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.util.UUID

object Groups : Table("\"group\"") {
    val id = varchar("id", 32).clientDefault { UUID.randomUUID().toString().replace("-", "") }
    val name = varchar("name", 100)
    val registeredDate = timestampWithTimeZone("\"registeredDate\"")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val pricing = enumerationByName<Pricing>("pricing", 16)
        .check("ck_group_pricing") { it inList Pricing.entries }

    override val primaryKey = PrimaryKey(id, name = "pk_group")
}
