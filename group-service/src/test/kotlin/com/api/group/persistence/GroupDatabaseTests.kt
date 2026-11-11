package com.api.group.persistence

import com.api.group.infrastructure.persistence.table.Groups
import com.api.group.infrastructure.persistence.table.GroupMembers

import com.api.group.domain.Permission
import com.api.group.domain.Pricing
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import java.time.ZoneOffset
import javax.sql.DataSource

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:group-schema-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "logging.file.name=build/test-logs/group-database.jsonl",
])
class GroupDatabaseTests @Autowired constructor(
    private val transactionManager: PlatformTransactionManager,
    dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val transactions = TransactionTemplate(transactionManager)

    @Test
    fun `group database contains no member table`() {
        assertTrue(transactionManager is SpringTransactionManager)
        val tables = jdbc.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'",
            String::class.java,
        )
        assertEquals(setOf("group", "group_member"), tables.toSet())
    }

    @Test
    fun `one group accepts several members with typed permissions`() = rollback {
        val groupId = createGroup(Pricing.Advanced)
        Permission.entries.forEachIndexed { index, permission -> join(groupId, "member$index", permission) }
        val memberships = GroupMembers.selectAll().where { GroupMembers.groupId eq groupId }.toList()
        assertEquals(3, memberships.size)
        assertEquals(Permission.entries.toSet(), memberships.map { it[GroupMembers.permission] }.toSet())
        assertTrue(memberships.all { it[GroupMembers.registeredDate].offset == ZoneOffset.UTC })
    }

    @Test
    fun `a member cannot join another group or join the same group twice`() = rollback {
        val first = createGroup(Pricing.Free)
        val second = createGroup(Pricing.Premium)
        join(first, "member200", Permission.Admin)
        assertThrows(SQLException::class.java) { join(second, "member200", Permission.Viewer) }
        assertThrows(SQLException::class.java) { join(first, "member200", Permission.Editor) }
        val membership = GroupMembers.selectAll().where { GroupMembers.memberId eq "member200" }.single()
        assertEquals(first, membership[GroupMembers.groupId])
        assertEquals(Permission.Admin, membership[GroupMembers.permission])
    }

    @Test
    fun `an unknown group is rejected by the foreign key`() = rollback {
        assertThrows(SQLException::class.java) { join("missingGroup", "member300", Permission.Viewer) }
    }

    @Test
    fun `database rejects pricing and permission values outside the supplied enums`() = rollback {
        val groupId = createGroup(Pricing.Premium)
        join(groupId, "member400", Permission.Editor)
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbc.update("UPDATE \"group\" SET PRICING = ? WHERE ID = ?", "Premiun", groupId)
        }
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbc.update("UPDATE \"group_member\" SET PERMISSION = ? WHERE MEMBER_ID = ?", "Owner", "member400")
        }
    }

    private fun createGroup(pricing: Pricing): String = Groups.insert {
        it[name] = "테스트 그룹"
        it[Groups.pricing] = pricing
    }[Groups.id]

    private fun join(groupId: String, memberId: String, permission: Permission) {
        GroupMembers.insert {
            it[GroupMembers.groupId] = groupId
            it[GroupMembers.memberId] = memberId
            it[GroupMembers.permission] = permission
        }
    }

    private fun rollback(block: () -> Unit) {
        transactions.executeWithoutResult { transaction ->
            transaction.setRollbackOnly()
            block()
        }
    }
}
