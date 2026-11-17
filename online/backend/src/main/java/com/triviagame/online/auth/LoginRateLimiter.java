package com.triviagame.online.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Per-address fixed window. Uses the socket address, never an untrusted forwarded header. */
@Component
public class LoginRateLimiter {
    private record Window(Instant start, int attempts) { }
    private final Map<String,Window> windows = new HashMap<>();
    private final Clock clock;
    private final int limit;
    public LoginRateLimiter(Clock clock, @Value("${app.login-attempts:10}") int limit) { this.clock=clock; this.limit=limit; }
    public synchronized boolean acquire(String address) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(e -> !e.getValue().start().plus(Duration.ofMinutes(5)).isAfter(now));
        Window old = windows.getOrDefault(address, new Window(now, 0));
        if (old.attempts() >= limit || (windows.size() >= 10000 && !windows.containsKey(address))) return false;
        windows.put(address, new Window(old.start(), old.attempts()+1));
        return true;
    }
    public synchronized void succeeded(String address) { windows.remove(address); }
}
