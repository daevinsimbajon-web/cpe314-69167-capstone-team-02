package com.triviagame.online;

import com.triviagame.online.auth.LoginRateLimiter;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LoginRateLimiterTest {
    @Test void blocksExcessAttemptsAndSuccessResetsOnlyThatAddress() {
        var limiter = new LoginRateLimiter(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),2);
        assertThat(limiter.acquire("one")).isTrue(); assertThat(limiter.acquire("one")).isTrue();
        assertThat(limiter.acquire("one")).isFalse(); assertThat(limiter.acquire("two")).isTrue();
        limiter.succeeded("one"); assertThat(limiter.acquire("one")).isTrue();
    }
    @Test void rateLimitExpiresAtTheFiveMinuteBoundary() {
        var clock=new HttpTestSupport.MutableClock();var limiter=new LoginRateLimiter(clock,1);
        assertThat(limiter.acquire("one")).isTrue();assertThat(limiter.acquire("one")).isFalse();
        clock.advance(Duration.ofMinutes(5));assertThat(limiter.acquire("one")).isTrue();
    }
}
