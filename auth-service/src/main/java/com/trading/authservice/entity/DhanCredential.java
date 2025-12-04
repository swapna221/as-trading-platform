package com.trading.authservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dhan_credentials")
@Getter
@Setter
public class DhanCredential {
    @Id @GeneratedValue private Long id;
    private String clientId;

    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @OneToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;
}

