package com.kestrel.commerce.shared.idempotency;

import com.kestrel.commerce.shared.domain.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord extends AssignedIdEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "scope", nullable = false, updatable = false)
    private String scope;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyRecord() {}

    IdempotencyRecord(UUID id, String scope, String idempotencyKey, String requestHash, Instant createdAt) {
        this.id = id;
        this.scope = scope;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    void complete(int status, String body, Instant when) {
        this.responseStatus = status;
        this.responseBody = body;
        this.completedAt = when;
    }

    boolean isCompleted() {
        return completedAt != null;
    }

    String getRequestHash() {
        return requestHash;
    }

    Integer getResponseStatus() {
        return responseStatus;
    }

    String getResponseBody() {
        return responseBody;
    }
}
