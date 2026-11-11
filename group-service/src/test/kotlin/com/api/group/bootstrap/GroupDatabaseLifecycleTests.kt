package com.api.group.bootstrap

import com.api.group.GroupServiceApplication
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

class GroupDatabaseLifecycleTests {
    @Test
    fun `startup seeds groups and memberships and shutdown removes all of them`() {
        val url = databaseUrl()
        start(url).use { context ->
            val jdbc = context.getBean(JdbcTemplate::class.java)
            assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM \"group\"", Long::class.java))
            assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM \"group_member\"", Long::class.java))
            jdbc.update("DELETE FROM \"group_member\" WHERE MEMBER_ID = 'ax1qwsd12s'")
            jdbc.update("UPDATE \"group\" SET \"name\" = 'changed' WHERE ID = 'arutjf9348'")
            DriverManager.getConnection("$url;IFEXISTS=TRUE", "sa", "").use { assertFalse(it.isClosed) }
        }
        assertThrows(SQLException::class.java) { DriverManager.getConnection("$url;IFEXISTS=TRUE", "sa", "").close() }
        start(url).use { context ->
            val jdbc = context.getBean(JdbcTemplate::class.java)
            assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM \"group\"", Long::class.java))
            assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM \"group_member\"", Long::class.java))
            assertEquals("Free 샘플 그룹", jdbc.queryForObject("SELECT \"name\" FROM \"group\" WHERE ID = 'arutjf9348'", String::class.java))
        }
        assertThrows(SQLException::class.java) { DriverManager.getConnection("$url;IFEXISTS=TRUE", "sa", "").close() }
    }

    @Test
    fun `sample groups can be disabled`() {
        start(databaseUrl(), "--app.sample-data.enabled=false").use { context ->
            val jdbc = context.getBean(JdbcTemplate::class.java)
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM \"group\"", Long::class.java))
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM \"group_member\"", Long::class.java))
        }
    }

    private fun databaseUrl() = "jdbc:h2:mem:groupLifecycle${UUID.randomUUID()};DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE"

    private fun start(url: String, vararg extra: String) = SpringApplicationBuilder(GroupServiceApplication::class.java)
        .web(WebApplicationType.NONE)
        .run("--spring.datasource.url=$url", "--logging.file.name=build/test-logs/group-lifecycle.jsonl", *extra)
}
