package com.kestrel.commerce.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads what the service published on the order events topic, like a downstream consumer would. */
public final class OrderEventsReader implements AutoCloseable {

    public static final String TOPIC = "commerce.order-events.v1";

    private final KafkaConsumer<String, String> consumer;
    private final JsonMapper jsonMapper;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    public OrderEventsReader(String bootstrapServers, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.consumer = new KafkaConsumer<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "test-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        "earliest"),
                new StringDeserializer(),
                new StringDeserializer());
        consumer.subscribe(List.of(TOPIC));
    }

    /** Polls once and returns the envelopes received so far for the given order, in publication order. */
    public List<JsonNode> eventsFor(UUID orderId) {
        consumer.poll(Duration.ofMillis(500)).forEach(received::add);
        return received.stream()
                .filter(record -> orderId.toString().equals(record.key()))
                .map(record -> jsonMapper.readTree(record.value()))
                .toList();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
