package com.triviagame.online.analytics;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final ExportSigning signing;

    public AnalyticsController(AnalyticsService analytics, ExportSigning signing) {
        this.analytics = analytics;
        this.signing = signing;
    }

    @GetMapping("/report")
    public AnalyticsExport report(
            Principal user,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "UTC") String zone) {
        return analytics.export(UUID.fromString(user.getName()), from, to, zone);
    }

    @GetMapping("/export")
    public ResponseEntity<AnalyticsExport> export(
            Principal user,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "UTC") String zone,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String mode,
            @RequestParam(defaultValue = "") String ruleset) {
        var filtered =
                analytics.filteredExport(
                        UUID.fromString(user.getName()), from, to, zone, category, mode, ruleset);
        var data = signing.sign(UUID.fromString(user.getName()), filtered);
        String filename =
                "hootly-my-stats-" + data.exportedAt().toString().substring(0, 10) + ".json";
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(data);
    }
}
