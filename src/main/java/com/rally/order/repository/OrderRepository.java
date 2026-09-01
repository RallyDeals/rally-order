package com.rally.order.repository;

import com.rally.order.model.CancelReason;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import com.rally.order.model.ShippingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    @Query("SELECT o.orderType FROM Order o WHERE o.id = :orderId")
    Optional<OrderType> findOrderTypeById(UUID orderId);

    @Modifying
    @Query("UPDATE Order o SET o.status = OrderStatus.CANCELLED, o.cancelReason = :cancelReason " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusToCancelledIfCurrent(UUID orderId, OrderStatus oldStatus, CancelReason cancelReason);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusIfCurrent(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus);

    @Modifying
    @Query("UPDATE Order o SET o.status = OrderStatus.CANCELLED, o.cancelReason = :cancelReason, o.paymentId = :paymentId " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusToCancelledWithPaymentIfCurrent(UUID orderId, OrderStatus oldStatus, CancelReason cancelReason, UUID paymentId);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.paymentId = :paymentId " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusWithPaymentIfCurrent(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus, UUID paymentId);

    @Modifying
    @Query("UPDATE Order o SET o.status = OrderStatus.PENDING_VOID, o.cancelReason = :cancelReason " +
            "WHERE o.id = :orderId AND o.status = :oldStatus")
    int updateStatusToPendingVoidIfCurrent(UUID orderId, OrderStatus oldStatus, CancelReason cancelReason);

    @Query(value = "SELECT * FROM orders WHERE status = :status AND status_updated_at < :threshold " +
            "ORDER BY status_updated_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Order> lockStaleOrders(String status, Instant threshold, int limit);

    List<Order> findOrdersByDealIdAndStatus(UUID dealId, OrderStatus status);

    Order findOrderByDealIdAndParticipantId(UUID dealId, UUID participantId);

    @Modifying
    @Query("UPDATE Order o SET o.shippingStatus = ShippingStatus.PROCESSING, o.shippingStatusUpdatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.id = :orderId AND o.shippingStatus IS NULL")
    void initializeShippingStatusIfNull(UUID orderId);

    @Modifying
    @Query("UPDATE Order o SET o.shippingStatus = :newStatus, o.shippingStatusUpdatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.shippingStatus = :oldStatus AND o.shippingStatusUpdatedAt < :threshold")
    int advanceShippingStatus(ShippingStatus oldStatus, ShippingStatus newStatus, OffsetDateTime threshold);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.orderProducts op " +
            "WHERE op.sellerId = :sellerId AND o.orderType = com.rally.order.model.OrderType.NORMAL " +
            "AND (:statuses IS NULL OR o.status IN :statuses) " +
            "AND (:shippingStatus IS NULL OR o.shippingStatus = :shippingStatus) " +
            "AND o.createdAt BETWEEN :startDate AND CURRENT_TIMESTAMP " +
            "AND (:search IS NULL OR LOWER(op.productName) LIKE CONCAT('%', LOWER(CAST(:search AS string)), '%'))")
    Page<Order> findOrdersBySellerIdAndFilters(UUID sellerId, List<OrderStatus> statuses, ShippingStatus shippingStatus,
                                               OffsetDateTime startDate, String search, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderProducts op " +
            "WHERE o.id IN :orderIds AND op.sellerId = :sellerId")
    List<Order> findByIdsWithSellerItems(List<UUID> orderIds, UUID sellerId);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderProducts op " +
            "WHERE o.id = :orderId AND op.sellerId = :sellerId AND o.orderType = com.rally.order.model.OrderType.NORMAL")
    Optional<Order> findByIdAndSellerId(UUID orderId, UUID sellerId);

    @Query("SELECT COUNT(DISTINCT o.id), " +
            "COUNT(DISTINCT CASE WHEN o.status NOT IN (com.rally.order.model.OrderStatus.CONFIRMED, com.rally.order.model.OrderStatus.CANCELLED) THEN o.id END), " +
            "COUNT(DISTINCT CASE WHEN o.status = com.rally.order.model.OrderStatus.CONFIRMED AND o.shippingStatus = com.rally.order.model.ShippingStatus.DELIVERED THEN o.id END), " +
            "COALESCE(SUM(CASE WHEN o.status = com.rally.order.model.OrderStatus.CONFIRMED THEN op.unitPrice * op.quantity ELSE 0 END), 0) " +
            "FROM Order o JOIN o.orderProducts op " +
            "WHERE op.sellerId = :sellerId AND o.orderType = com.rally.order.model.OrderType.NORMAL " +
            "AND o.createdAt BETWEEN :startDate AND CURRENT_TIMESTAMP ")
    List<Object[]> getSellerOrdersAnalyticsRaw(UUID sellerId, OffsetDateTime startDate);


    @Query("SELECT " +
            "COUNT(CASE WHEN o.status = com.rally.order.model.OrderStatus.CONFIRMED " +
            "AND o.shippingStatus = com.rally.order.model.ShippingStatus.DELIVERED THEN 1 END), " +
            "COUNT(CASE WHEN o.status = com.rally.order.model.OrderStatus.CANCELLED THEN 1 END), " +
            "COUNT(CASE WHEN o.status IN (" +
            "com.rally.order.model.OrderStatus.PENDING_CHARGE, " +
            "com.rally.order.model.OrderStatus.PENDING_AUTHORIZATION, " +
            "com.rally.order.model.OrderStatus.PENDING_CAPTURE, " +
            "com.rally.order.model.OrderStatus.PENDING_VOID" +
            ") THEN 1 END), " +
            "COUNT(CASE WHEN o.status = com.rally.order.model.OrderStatus.CONFIRMED " +
            "AND o.shippingStatus IN (" +
            "com.rally.order.model.ShippingStatus.PROCESSING, " +
            "com.rally.order.model.ShippingStatus.SHIPPING" +
            ") THEN 1 END) " +
            "FROM Order o WHERE o.userId = :userId")
    List<Object[]> getBuyerOrdersAnalyticsRaw(UUID userId);

    List<Order> findByUserId(UUID userId);
}
