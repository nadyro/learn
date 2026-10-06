package com.kestrel.commerce.shared.idempotency;

import com.kestrel.commerce.shared.domain.Ids;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes an operation safe to retry with the same {@code Idempotency-Key} header.
 *
 * <p>Networks fail: a client may never receive the response to a "place order" request even though the order was
 * created. With an idempotency key the client can safely retry; the second request returns the response of the first
 * one instead of creating a second order (and charging the customer twice).
 *
 * <p>How it works, all inside the caller's database transaction:
 *
 * <ol>
 *   <li>If a record exists for (scope, key): replay its stored response, or reject the request if its body differs.
 *   <li>Otherwise insert a record <i>first</i>. A concurrent request with the same key blocks on the unique index until
 *       we commit, then fails and gets a 409 (the client retries and receives the replay).
 *   <li>Run the operation and store its response in the record.
 * </ol>
 *
 * <p>If the operation fails, the transaction rolls back, the record disappears and the client can retry.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * @param scope namespaces keys, e.g. by operation and user, so two customers can use the same key value
     * @param key the client-provided idempotency key
     * @param request the request body, used to detect a key reused for a different request
     * @param successStatus HTTP status of a successful response, stored for replays
     * @param responseType type of the response, used to deserialize a replay
     * @param operation the business operation, executed at most once per key
     */
    @Transactional
    public <T> IdempotentResult<T> execute(
            String scope, String key, Object request, int successStatus, Class<T> responseType, Supplier<T> operation) {
        String requestHash = hash(request);

        Optional<IdempotencyRecord> existing = repository.findByScopeAndIdempotencyKey(scope, key);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, responseType);
        }

        IdempotencyRecord record = reserveKey(scope, key, requestHash);
        T response = operation.get();
        record.complete(successStatus, jsonMapper.writeValueAsString(response), clock.instant());
        return new IdempotentResult<>(response, false);
    }

    private IdempotencyRecord reserveKey(String scope, String key, String requestHash) {
        try {
            return repository.saveAndFlush(
                    new IdempotencyRecord(Ids.newId(), scope, key, requestHash, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw inFlight();
        }
    }

    private <T> IdempotentResult<T> replay(IdempotencyRecord record, String requestHash, Class<T> responseType) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new DomainException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different request. Use a new key for a new request.");
        }
        if (!record.isCompleted()) {
            throw inFlight();
        }
        return new IdempotentResult<>(jsonMapper.readValue(record.getResponseBody(), responseType), true);
    }

    private static DomainException inFlight() {
        return new DomainException(
                ErrorCode.IDEMPOTENCY_KEY_IN_FLIGHT,
                "A request with this Idempotency-Key is still being processed. Retry in a moment.",
                Map.of("retryAfterSeconds", 1));
    }

    private String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(jsonMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
