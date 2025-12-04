package com.trading.authservice.repository;


import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.trading.authservice.entity.DhanCredential;

public interface DhanCredentialRepository extends JpaRepository<DhanCredential, Long> {
    Optional<DhanCredential> findByUserId(Long userId);
}

