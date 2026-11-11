package com.api.member.infrastructure.persistence

import com.api.member.application.query.GetMembersQuery
import com.api.member.application.query.MemberNameOrder
import com.api.member.application.query.MemberReadPort
import com.api.member.application.query.MemberSummary
import com.api.member.application.query.MemberDetailReadPort
import com.api.member.application.query.MemberDetailsRecord
import com.api.member.infrastructure.persistence.table.Members
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ExposedMemberReadAdapter : MemberReadPort, MemberDetailReadPort {

    @Transactional(readOnly = true)
    override fun findById(memberId: String): MemberDetailsRecord? =
        Members.selectAll().where { Members.id eq memberId }.singleOrNull()?.let {
            MemberDetailsRecord(it[Members.id], it[Members.name], it[Members.email], it[Members.address], it[Members.registeredDate])
        }

    @Transactional(readOnly = true)
    override fun findPage(query: GetMembersQuery): List<MemberSummary> {
        val rows = Members.select(Members.id, Members.name)
        query.order?.let {
            val direction = if (it == MemberNameOrder.ASC) SortOrder.ASC else SortOrder.DESC
            rows.orderBy(Members.name to direction, Members.id to SortOrder.ASC)
        }
        return rows.limit(query.size).offset(query.page.toLong() * query.size).map {
            MemberSummary(it[Members.id], it[Members.name])
        }
    }
}
