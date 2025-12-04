package com.trading.decisionenginesvc.repository;


import com.trading.decisionenginesvc.Entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {
}
