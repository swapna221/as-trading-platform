package com.trading.manualorderservice.repo;

import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.entity.OrderRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByParentOrderIdAndRole(Long parentId, OrderRole role);

    /**
     * All entries where SL or TGT monitoring is needed (OCO).
     * Not restricted by trailing mode.
     */
    @Query("""
       SELECT e FROM OrderEntity e
       WHERE e.role = com.trading.manualorderservice.entity.OrderRole.ENTRY
         AND e.workflow IN ('EQUITY_INTRADAY', 'OPTION')
         AND e.orderStatus IN ('FILLED','OPEN','PENDING','TRIGGER_PENDING')
    """)
    List<OrderEntity> findEntriesForOco();

    @Query("""
   SELECT e FROM OrderEntity e
   WHERE e.role = com.trading.manualorderservice.entity.OrderRole.ENTRY
     AND e.workflow IN ('EQUITY_INTRADAY', 'OPTION')
     AND e.trailingPercent IS NOT NULL
     AND e.trailingPercent > 0
     AND e.orderStatus IN ('FILLED', 'COMPLETED')
   """)
    List<OrderEntity> findActiveEntriesWithTrailing();



    /**
     * Find active SL order for an entry.
     */
    @Query("""
           SELECT o FROM OrderEntity o
           WHERE o.role = com.trading.manualorderservice.entity.OrderRole.STOPLOSS
             AND o.parentOrderId = :entryId
             AND o.orderStatus IN ('OPEN','PENDING','TRIGGER_PENDING','RECEIVED')
           """)
    OrderEntity findActiveSlOrder(@Param("entryId") Long entryId);


    /**
     * Find active TARGET order.
     */
    @Query("""
       SELECT o FROM OrderEntity o
       WHERE o.parentOrderId = :entryId
         AND o.role = com.trading.manualorderservice.entity.OrderRole.TARGET
         AND o.orderStatus IN ('OPEN','PENDING','TRIGGER_PENDING','RECEIVED')
       """)
    OrderEntity findActiveTargetOrder(@Param("entryId") Long entryId);


    /**
     * Check if SL is filled.
     */
    @Query("""
       SELECT o FROM OrderEntity o
       WHERE o.parentOrderId = :entryId
         AND o.role = com.trading.manualorderservice.entity.OrderRole.STOPLOSS
         AND o.orderStatus IN ('FILLED','COMPLETED','EXECUTED')
       """)
    OrderEntity findFilledSlOrder(@Param("entryId") Long entryId);


    /**
     * Check if TARGET is filled.
     */
    @Query("""
       SELECT o FROM OrderEntity o
       WHERE o.parentOrderId = :entryId
         AND o.role = com.trading.manualorderservice.entity.OrderRole.TARGET
         AND o.orderStatus IN ('FILLED','COMPLETED','EXECUTED')
       """)
    OrderEntity findFilledTargetOrder(@Param("entryId") Long entryId);


    @Query("""
    SELECT o FROM OrderEntity o
    WHERE o.orderStatus = 'TRANSIT'
    """)
    List<OrderEntity> findOrdersInTransit();


    @Query("""
    SELECT o FROM OrderEntity o
    WHERE o.orderStatus IN ('FILLED','OPEN','PENDING','TRIGGER_PENDING')
    """)
    List<OrderEntity> findOrdersForLtpRefresh();
}
