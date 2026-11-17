package com.triviagame.online.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns user-scoped reporting queries; account and history endpoints share these definitions. */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final AnalyticsRepository repository;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AnalyticsExport export(UUID user, LocalDate from, LocalDate to, String zone) {
        ZoneId timezone;
        try {
            timezone = ZoneId.of(zone);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("Choose a valid time zone");
        }
        LocalDate start = from == null ? LocalDate.of(1970, 1, 1) : from,
                end = to == null ? LocalDate.of(9998, 12, 31) : to;
        if (end.isBefore(start))
            throw new IllegalArgumentException("End date must not precede start date");
        return repository.read(
                user,
                start.atStartOfDay(timezone).toInstant(),
                end.plusDays(1).atStartOfDay(timezone).toInstant(),
                clock.instant());
    }

    public Map<String, Object> statistics(UUID user) {
        return repository.statistics(user);
    }

    public List<Map<String, Object>> history(UUID user) {
        return repository.history(user);
    }

    public List<Map<String, Object>> categoryAccuracy(UUID user) {
        return repository.categoryAccuracy(user);
    }

    public AnalyticsExport filteredExport(
            UUID user,
            LocalDate from,
            LocalDate to,
            String zone,
            String category,
            String mode,
            String ruleset) {
        return AnalyticsFilters.apply(export(user, from, to, zone), category, mode, ruleset);
    }
}
