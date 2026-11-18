package com.triviagame.online;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** All personal-quiz HTTP scenarios against disposable PostgreSQL, never the user's database. */
@Testcontainers
class PersonalQuizPostgresIT extends PersonalQuizTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry config){
        config.add("spring.datasource.url",postgres::getJdbcUrl);
        config.add("spring.datasource.username",postgres::getUsername);
        config.add("spring.datasource.password",postgres::getPassword);
        config.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
}
