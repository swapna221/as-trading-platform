package com.trading.manualorderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dhan_credentials")
@Getter
@Setter
public class DhanCredentialEntity {

    @Id
    private Long id;

    private String clientId;

    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "user_id")
    private Long userId;
}

