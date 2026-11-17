package com.triviagame.online.web;

import com.triviagame.online.analytics.AnalyticsService;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class HistoryController {
    private final AnalyticsService analytics;
    public HistoryController(AnalyticsService analytics){this.analytics=analytics;}
    @GetMapping("/history") List<Map<String,Object>> history(Principal user){
        return analytics.history(UUID.fromString(user.getName()));
    }
    @GetMapping("/analytics") List<Map<String,Object>> analytics(Principal user){
        return analytics.categoryAccuracy(UUID.fromString(user.getName()));
    }
}
