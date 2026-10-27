package com.campusfootball;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWechatConfig.class)
public abstract class IntegrationTestBase {

    private static final String EXTERNAL_DB_URL = System.getenv("TEST_DB_URL");
    private static final boolean USE_EXTERNAL_DB = EXTERNAL_DB_URL != null && !EXTERNAL_DB_URL.isBlank();
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    static {
        if (!USE_EXTERNAL_DB) {
            MYSQL.start();
            Runtime.getRuntime().addShutdownHook(new Thread(MYSQL::stop));
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_DB) {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB_URL);
            registry.add("spring.datasource.username", () -> envOrDefault("TEST_DB_USERNAME", "campus"));
            registry.add("spring.datasource.password", () -> envOrDefault("TEST_DB_PASSWORD", "campus"));
        } else {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
