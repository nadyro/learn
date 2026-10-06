package com.kestrel.commerce.shared.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the next batch of unpublished events.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} lets several replicas run the relay concurrently: each replica gets a disjoint
     * set of rows instead of blocking on (or double-publishing) rows another replica is already handling.
     */
    @Query(value = """
            select * from outbox_events
            where published_at is null
            order by occurred_at, id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(int batchSize);

    long countByPublishedAtIsNull();
}
