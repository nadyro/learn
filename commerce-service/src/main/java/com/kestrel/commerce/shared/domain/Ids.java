package com.kestrel.commerce.shared.domain;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates identifiers for our aggregates.
 *
 * <p>We use time-ordered UUIDs (RFC 9562, version 7) instead of random v4 UUIDs: they are still globally unique
 * and can be generated without a database round-trip, but consecutive IDs are close to each other in the B-tree
 * index, which keeps inserts cheap on large tables.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static UUID newId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);

        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) (timestamp >>> (40 - 8 * i));
        }
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70); // version 7
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80); // IETF variant

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
