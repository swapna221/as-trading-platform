package com.trading.astropredictionsvc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.trading.astropredictionsvc.publisher.AstroSignalPublisher;
import com.trading.shareddto.shareddto.AstroSignalEvent;

@RestController
@RequestMapping("/astrology")
public class AstroController {
	
	private final AstroSignalPublisher publisher;

    public AstroController(AstroSignalPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/publish")
    public ResponseEntity<String> publishSample(@RequestBody AstroSignalEvent event ) {      
        publisher.publishTrend(event);
        return ResponseEntity.ok("Astro published");
    }

}


