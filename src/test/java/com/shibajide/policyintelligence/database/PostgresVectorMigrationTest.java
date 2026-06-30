package com.shibajide.policyintelligence.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.security.enabled=false",
        "app.ml.enabled=false",
        "app.llm.enabled=false",
        "app.retrieval.cache.backend=LOCAL",
        "app.embeddings.backfill-initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
class PostgresVectorMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("policy_intelligence")
            .withUsername("policy")
            .withPassword("policy");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesPgVectorMigrations() {
        Integer extensionCount = jdbcTemplate.queryForObject(
                "select count(*) from pg_extension where extname = 'vector'",
                Integer.class
        );
        Integer tenantStateColumns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_name = 'corpus_state' and column_name = 'tenant_id'",
                Integer.class
        );

        assertThat(extensionCount).isEqualTo(1);
        assertThat(tenantStateColumns).isEqualTo(1);
    }
}
