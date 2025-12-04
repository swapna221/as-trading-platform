package com.trading.earningsanalysissvc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @GetMapping("/test/ping")
    public String ping() {
        return "earnings-analysis-svc is alive";
    }
}
