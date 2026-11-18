package com.triviagame.gameplay;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Monotonic, injectable turn clock. Reading the clock never changes its deadline. */
final class TurnTimer {
  record Reading(long elapsedMillis, int remainingSeconds, boolean timedOut) {}

  private final LongSupplier nanos;
  private final long started;
  private final long limitNanos;

  TurnTimer(int seconds, LongSupplier nanos) {
    if (seconds <= 0) throw new IllegalArgumentException("Time limit must be positive");
    this.nanos = Objects.requireNonNull(nanos);
    this.started = nanos.getAsLong();
    this.limitNanos = TimeUnit.SECONDS.toNanos(seconds);
  }

  Reading read() {
    long elapsed = Math.max(0, nanos.getAsLong() - started);
    long bounded = Math.min(limitNanos, elapsed);
    long left = limitNanos - bounded;
    int seconds = (int) ((left + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
    return new Reading(TimeUnit.NANOSECONDS.toMillis(bounded), seconds, elapsed >= limitNanos);
  }
}
