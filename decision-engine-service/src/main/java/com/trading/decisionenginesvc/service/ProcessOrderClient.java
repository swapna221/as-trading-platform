package com.trading.decisionenginesvc.service;

import com.trading.shareddto.shareddto.OrderEvent;
import com.trading.decisionenginesvc.dto.ProcessOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class ProcessOrderClient {

    private static final String PROCESS_ORDER_URL = "http://process-order-service/api/process/place";

    @Autowired
    private RestTemplate restTemplate;

    public void placeOrder() {}
}
