package com.rally.order.repository;

import com.rally.order.model.CancelReason;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.cancelReason = :cancelReason " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusToCancelledIfCurrent(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus, CancelReason cancelReason);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusIfCurrent(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus);
}
