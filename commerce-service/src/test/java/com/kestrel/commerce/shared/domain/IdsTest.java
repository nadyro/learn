package com.kestrel.commerce.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void ids_are_version_7_uuids() {
        UUID id = Ids.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void ids_embed_the_creation_time_so_they_sort_chronologically() throws InterruptedException {
        UUID earlier = Ids.newId();
        Thread.sleep(2);
        UUID later = Ids.newId();

        assertThat(later.toString()).isGreaterThan(earlier.toString());
        long millis = earlier.getMostSignificantBits() >>> 16;
        assertThat(millis).isCloseTo(System.currentTimeMillis(), within(5_000L));
    }

    @Test
    void ids_are_unique() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Ids.newId());
        }
        assertThat(ids).hasSize(10_000);
    }
}
