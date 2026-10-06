package com.kestrel.commerce.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and Kafka for integration tests, started once and shared by every test class using the same Spring
 * context.
 *
 * <p>{@code @ServiceConnection} makes Spring Boot point the datasource and Kafka clients at the containers, no
 * property juggling needed. Keep the image versions identical to compose.yaml and production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine");
    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.2.1");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }
}
