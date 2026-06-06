package com.cody.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "version", "2.0.0");
    }
}
