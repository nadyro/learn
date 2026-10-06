package com.kestrel.commerce.shared.outbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox configuration ({@code commerce.outbox.*}).
 *
 * @param topics Kafka topic for each aggregate type, e.g. {@code order -> commerce.order-events.v1}
 * @param createTopics create the topics at startup. Only for local development: in real environments topics are
 *     provisioned by the platform team with the right partitions, replication and retention.
 */
@Validated
@ConfigurationProperties(prefix = "commerce.outbox")
public record OutboxProperties(
        @NotEmpty Map<String, String> topics,
        @DefaultValue("false") boolean createTopics,
        @Valid @NotNull Relay relay) {

    /**
     * @param enabled turn the relay off to stop publishing (events keep accumulating safely in the table)
     * @param interval delay between two polling cycles
     * @param batchSize events locked and published per transaction
     * @param sendTimeout maximum time to wait for Kafka to acknowledge a message
     */
    public record Relay(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT1S") @NotNull Duration interval,
            @DefaultValue("100") @Min(1) @Max(1000) int batchSize,
            @DefaultValue("PT10S") @NotNull Duration sendTimeout) {}

    public String topicFor(String aggregateType) {
        String topic = topics.get(aggregateType);
        if (topic == null) {
            throw new IllegalStateException("No Kafka topic configured for aggregate type '" + aggregateType
                    + "'. Add commerce.outbox.topics." + aggregateType + " to the configuration.");
        }
        return topic;
    }
}
