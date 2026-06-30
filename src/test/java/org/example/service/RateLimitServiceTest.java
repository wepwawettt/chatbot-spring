package org.example.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {
    @Test
    void blocksRequestsAfterConfiguredLimit() {
        RateLimitService service = new RateLimitService(2, 60);

        assertThat(service.consumeChat("selin").allowed()).isTrue();
        assertThat(service.consumeChat("selin").allowed()).isTrue();

        RateLimitService.RateLimitResult blocked = service.consumeChat("selin");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isPositive();
    }

    @Test
    void tracksUsersIndependently() {
        RateLimitService service = new RateLimitService(1, 60);

        assertThat(service.consumeChat("selin").allowed()).isTrue();
        assertThat(service.consumeChat("selin").allowed()).isFalse();
        assertThat(service.consumeChat("burak").allowed()).isTrue();
    }
}
