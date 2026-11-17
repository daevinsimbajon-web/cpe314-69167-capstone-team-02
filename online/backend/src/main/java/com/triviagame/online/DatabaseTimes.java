package com.triviagame.online;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.Timestamp;

/** JDBC map queries use Timestamp on PostgreSQL and OffsetDateTime on H2. */
public final class DatabaseTimes {
    private DatabaseTimes() { }
    public static Instant instant(Object value) {
        if(value instanceof OffsetDateTime time)return time.toInstant();
        if(value instanceof Timestamp time)return time.toInstant();
        if(value instanceof Instant time)return time;
        throw new IllegalArgumentException("Unsupported database timestamp");
    }
}
