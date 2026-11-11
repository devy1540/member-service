package com.api.member.bootstrap

import com.api.member.MemberServiceApplication
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

class MemberDatabaseLifecycleTests {
    @Test
    fun `startup seeds members and context shutdown removes the database before reseeding`() {
        val url = databaseUrl()
        start(url).use { context ->
            val jdbc = context.getBean(JdbcTemplate::class.java)
            assertEquals(7L, jdbc.queryForObject("SELECT COUNT(*) FROM \"member\"", Long::class.java))
            jdbc.update("UPDATE \"member\" SET \"name\" = 'changed' WHERE ID = 'ax1qwsd12s'")
            jdbc.update("INSERT INTO \"member\" (ID, \"name\", EMAIL, ADDRESS) VALUES ('temporary', 'temporary', 'temporary@example.test', 'temporary')")
            DriverManager.getConnection("$url;IFEXISTS=TRUE", "sa", "").use { assertFalse(it.isClosed) }
        }
        assertThrows(SQLException::class.java) { DriverManager.getConnection("$url;IFEXISTS=TRUE", "sa", "").close() }
        start(url).use { context ->
            val jdbc = context.getBean(JdbcTemplate::class.java)
            assertEquals(7L, jdbc.queryForObject("SELECT COUNT(*) FROM \"member\"", Long::class.java))
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM \"member\" WHERE ID = 'temporary'", Long::class.java))
            assertEquals("김멤버", jdbc.queryForObject("SELECT \"name\" FROM \"member\" WHERE ID = 'ax1qwsd12s'", String::class.java))
        }
        assertThrows(SQLException::class.java) { DriverManager.getConnection("$url;IFEXISTS=TRUE", "sa", "").close() }
    }

    @Test
    fun `sample data can be disabled without disabling schema creation`() {
        start(databaseUrl(), "--app.sample-data.enabled=false").use { context ->
            assertEquals(0L, context.getBean(JdbcTemplate::class.java)
                .queryForObject("SELECT COUNT(*) FROM \"member\"", Long::class.java))
        }
    }

    private fun databaseUrl() = "jdbc:h2:mem:memberLifecycle${UUID.randomUUID()};DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE"

    private fun start(url: String, vararg extra: String) = SpringApplicationBuilder(MemberServiceApplication::class.java)
        .web(WebApplicationType.NONE)
        .run("--spring.datasource.url=$url", "--logging.file.name=build/test-logs/member-lifecycle.jsonl", *extra)
}
