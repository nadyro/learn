package com.kestrel.commerce.shared.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** Creates the outbox topics on start-up. Enabled for local development and tests only. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "commerce.outbox.create-topics")
public class KafkaTopicsConfig {

    private static final int LOCAL_PARTITIONS = 3;

    @Bean
    KafkaAdmin.NewTopics outboxTopics(OutboxProperties properties) {
        NewTopic[] topics = properties.topics().values().stream()
                .distinct()
                .map(name -> TopicBuilder.name(name)
                        .partitions(LOCAL_PARTITIONS)
                        .replicas(1)
                        .build())
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }
}
