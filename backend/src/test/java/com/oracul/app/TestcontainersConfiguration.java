package com.oracul.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for integration tests. Public so tests in every capability package can import it.
 *
 * <p>One container per JVM (static singleton, reaped by Ryuk at JVM exit), shared by every cached Spring context.
 * Each context gets its own database inside that server (created on context start, dropped on context close),
 * so Flyway migrates once per database and tests keep the per-context isolation they always had.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
        .withStartupTimeout(Duration.ofSeconds(60));
    private static final AtomicInteger SEQ = new AtomicInteger();

    static {
        POSTGRES.start();
    }

    @Bean
    ContextDatabase contextDatabase() throws SQLException {
        String name = "ctx_" + ProcessHandle.current().pid() + "_" + SEQ.incrementAndGet();
        admin("create database " + name);
        return new ContextDatabase(name);
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails(ContextDatabase db) {
        return new JdbcConnectionDetails() {
            @Override
            public String getJdbcUrl() {
                return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + db.name();
            }

            @Override
            public String getUsername() {
                return POSTGRES.getUsername();
            }

            @Override
            public String getPassword() {
                return POSTGRES.getPassword();
            }
        };
    }

    private static void admin(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    /** Name of this context's database; dropped when the context closes (after the DataSource beans are destroyed). */
    record ContextDatabase(String name) implements DisposableBean {
        @Override
        public void destroy() {
            try {
                admin("drop database if exists " + name + " with (force)");
            } catch (SQLException ignored) {
                // best effort; the container disappears with the JVM
            }
        }
    }
}
