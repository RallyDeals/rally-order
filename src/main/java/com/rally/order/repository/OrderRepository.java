package com.rally.order.repository;

import com.rally.order.model.CancelReason;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Modifying
    @Query("UPDATE Order o SET o.status = OrderStatus.CANCELLED, o.cancelReason = :cancelReason " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusToCancelledIfCurrent(UUID orderId, OrderStatus oldStatus, CancelReason cancelReason);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusIfCurrent(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus);

    @Modifying
    @Query("UPDATE Order o SET o.status = OrderStatus.CANCELLED, o.cancelReason = :cancelReason, o.paymentId = :paymentId, o.paymentIntentId = :paymentIntentId " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusToCancelledWithPaymentIfCurrent(UUID orderId, OrderStatus oldStatus, CancelReason cancelReason, UUID paymentId, String paymentIntentId);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.paymentId = :paymentId, o.paymentIntentId = :paymentIntentId " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusWithPaymentIfCurrent(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus, UUID paymentId, String paymentIntentId);

    @Modifying
    @Query("UPDATE Order o SET o.status = OrderStatus.PENDING_VOID, o.cancelReason = :cancelReason " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusToPendingVoidIfCurrent(UUID orderId, OrderStatus oldStatus, CancelReason cancelReason);

    @Query(value = "SELECT * FROM orders WHERE status = :status AND status_updated_at < :threshold " +
            "ORDER BY status_updated_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Order> lockStaleOrders(String status, Instant threshold, int limit);

    List<Order> findOrdersByDealIdAndStatus(UUID dealId, OrderStatus status);

    Order findOrderByDealIdAndParticipantId(UUID dealId, UUID participantId);

    List<Order> findByUserId(UUID userId);

    @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:orderType IS NULL OR o.orderType = :orderType)")
    Page<Order> findByUserIdAndFilters(UUID userId, OrderStatus status, OrderType orderType, Pageable pageable);
}
