package com.hireflow.web.controller;

import com.hireflow.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.dashboard(days);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.overview(days);
    }

    @GetMapping("/ai-usage")
    public Map<String, Object> aiUsage(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.aiUsage(days);
    }
}
