package com.trading.shareddto.entity;

import lombok.*;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Getter
@Setter
public class BrokerUserDetails implements Serializable {
    long userId;
    String accessToken;
    String clientId;
}