package com.kestrel.commerce.shared.domain;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base class for entities whose ID is assigned by the application and that have no {@code @Version} column (append-only
 * tables such as the outbox).
 *
 * <p>Why it exists: Spring Data's {@code save()} decides between {@code persist} (INSERT) and {@code merge}
 * (SELECT + INSERT/UPDATE) by checking if the entity is "new". By default an entity with a non-null ID is considered
 * existing, so every insert would cost an extra SELECT. Implementing {@link Persistable} tells Spring Data the truth.
 */
@MappedSuperclass
public abstract class AssignedIdEntity<ID> implements Persistable<ID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
