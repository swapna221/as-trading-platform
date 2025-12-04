package com.trading.manualorderservice.repo;

import com.trading.manualorderservice.entity.DhanCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DhanCredentialRepository extends JpaRepository<DhanCredentialEntity, Long> {

    DhanCredentialEntity findByUserId(Long userId);
}

