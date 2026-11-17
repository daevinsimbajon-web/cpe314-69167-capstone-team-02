package com.triviagame.online;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** The same consent, import and privacy scenarios run against real PostgreSQL migrations. */
@Testcontainers
class StatisticsSharingPostgresIT extends StatisticsSharingTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
        properties.add("spring.datasource.url",postgres::getJdbcUrl);properties.add("spring.datasource.username",postgres::getUsername);
        properties.add("spring.datasource.password",postgres::getPassword);properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
}
