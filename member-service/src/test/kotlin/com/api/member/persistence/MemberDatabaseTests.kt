package com.api.member.persistence

import com.api.member.infrastructure.persistence.table.Members

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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZoneOffset
import javax.sql.DataSource

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:member-schema-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "logging.file.name=build/test-logs/member-database.jsonl",
])
class MemberDatabaseTests @Autowired constructor(
    private val transactionManager: PlatformTransactionManager,
    dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val transactions = TransactionTemplate(transactionManager)

    @Test
    fun `only the member table exists and Exposed manages transactions`() {
        assertTrue(transactionManager is SpringTransactionManager)
        val tables = jdbc.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'",
            String::class.java,
        )
        assertEquals(setOf("member"), tables.toSet())
    }

    @Test
    fun `member data round trips and registration time defaults to UTC`() {
        transactions.executeWithoutResult { transaction ->
            transaction.setRollbackOnly()
            val id = Members.insert {
                it[name] = "테스트 멤버"
                it[email] = "test@example.test"
                it[address] = "서울특별시"
            }[Members.id]

            val member = Members.selectAll().where { Members.id eq id }.single()
            assertTrue(Regex("[a-f0-9]{32}").matches(id))
            assertEquals("테스트 멤버", member[Members.name])
            assertEquals("test@example.test", member[Members.email])
            assertEquals("서울특별시", member[Members.address])
            assertEquals(ZoneOffset.UTC, member[Members.registeredDate].offset)
        }
    }

    @Test
    fun `failed Spring transactions roll back Exposed writes`() {
        assertThrows(IllegalStateException::class.java) {
            transactions.executeWithoutResult {
                Members.insert {
                    it[name] = "롤백 멤버"
                    it[email] = "rollback@example.test"
                    it[address] = "서울특별시"
                }
                error("rollback fixture")
            }
        }
        transactions.executeWithoutResult {
            assertEquals(0L, Members.selectAll().where { Members.email eq "rollback@example.test" }.count())
        }
    }
}
