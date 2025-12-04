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
     * All active ENTRY orders that have trailing SL enabled.
     */
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
     * Currently active SL order for this entry.
     */
    @Query("""
           SELECT o FROM OrderEntity o
           WHERE o.role = com.trading.manualorderservice.entity.OrderRole.STOPLOSS
             AND o.parentOrderId = :entryId
             AND o.orderStatus IN ('OPEN', 'PENDING', 'TRIGGER_PENDING', 'RECEIVED')
           """)
    OrderEntity findActiveSlOrder(@Param("entryId") Long entryId);


    /**
     * Currently active TARGET order for this entry.
     */
    @Query("""
       SELECT o FROM OrderEntity o
       WHERE o.parentOrderId = :entryId
         AND o.role = com.trading.manualorderservice.entity.OrderRole.TARGET
         AND o.orderStatus IN ('OPEN','PENDING','TRIGGER_PENDING','RECEIVED')
       """)
    OrderEntity findActiveTargetOrder(@Param("entryId") Long entryId);


    /**
     * Check if SL has been filled.
     */
    @Query("""
       SELECT o FROM OrderEntity o
       WHERE o.parentOrderId = :entryId
         AND o.role = com.trading.manualorderservice.entity.OrderRole.STOPLOSS
         AND o.orderStatus IN ('FILLED','COMPLETED','EXECUTED')
       """)
    OrderEntity findFilledSlOrder(@Param("entryId") Long entryId);


    /**
     * Check if TARGET has been filled.
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
    WHERE o.orderStatus IN ('TRANSIT')
    """)
    List<OrderEntity> findOrdersInTransit();

    @Query("""
    SELECT o FROM OrderEntity o
    WHERE o.orderStatus IN ('FILLED','OPEN','PENDING','TRIGGER_PENDING')
""")
    List<OrderEntity> findOrdersForLtpRefresh();



}
