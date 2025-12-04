package com.trading.decisionenginesvc.service;

import com.trading.shareddto.shareddto.TradeDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trading.shareddto.shareddto.OrderEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrderProcessor {

    @Autowired
    ProcessOrderClient processOrderClient;


    public void processOrder(TradeDto trade){

    }






}
