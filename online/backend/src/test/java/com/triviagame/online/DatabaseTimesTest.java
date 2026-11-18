package com.triviagame.online;

import java.sql.Timestamp;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DatabaseTimesTest {
    @Test
    void rejectsMissingAndUnsupportedDatabaseValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> DatabaseTimes.instant(null));
        assertThatIllegalArgumentException().isThrownBy(() -> DatabaseTimes.instant("2026-01-01"));
    }

    @Test void bothJdbcDriversPreserveTheSameInstant(){
        Instant now=Instant.parse("2026-01-01T23:59:59.999Z");
        assertThat(DatabaseTimes.instant(Timestamp.from(now))).isEqualTo(now);
        assertThat(DatabaseTimes.instant(OffsetDateTime.ofInstant(now,ZoneOffset.ofHours(8)))).isEqualTo(now);
    }
}
