package com.trading.candlepatternsvc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trading.candlepatternsvc.service.TrendSignalPublisher;
import com.trading.shareddto.shareddto.TrendSignalEvent;

@RestController
@RequestMapping("/trend")
public class TrendSignalController {

    private final TrendSignalPublisher publisher;

    public TrendSignalController(TrendSignalPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/publish")
    public ResponseEntity<String> publishSample(@RequestBody TrendSignalEvent event ) {      
        publisher.publishTrend(event);
        return ResponseEntity.ok("Trend published");
    }
}

