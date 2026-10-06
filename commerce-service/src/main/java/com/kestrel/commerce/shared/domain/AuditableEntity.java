package com.kestrel.commerce.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base class for aggregate roots.
 *
 * <ul>
 *   <li>{@code created_at}/{@code updated_at} are maintained by Spring Data auditing (see {@code JpaConfig}).
 *   <li>{@code version} enables optimistic locking: two transactions updating the same row concurrently cannot both
 *       win, the second one fails with an {@code OptimisticLockingFailureException} (mapped to HTTP 409).
 *   <li>A {@code null} version tells Spring Data the entity is new, so {@code save()} issues an INSERT without a
 *       preliminary SELECT even though our IDs are assigned by the application.
 * </ul>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
