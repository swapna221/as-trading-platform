package com.trading.fiidiitrackersvc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @GetMapping("/test/ping")
    public String ping() {
        return "fii-dii-tracker-svc is alive";
    }
}
