package com.rally.order.mapper;

import com.rally.order.dto.BriefOrderResponse;
import com.rally.order.dto.BriefSellerOrderResponse;
import com.rally.order.dto.CheckOutOrderResponse;
import com.rally.order.dto.CompactedOrderStatus;
import com.rally.order.dto.DetailedSellerOrderResponse;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.dto.OrderProductResponse;
import com.rally.order.model.Order;
import com.rally.order.model.OrderProduct;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    CheckOutOrderResponse toCheckoutOrderResponse(Order order);

    List<OrderProductResponse> toOrderProductResponses(List<OrderProduct> orderProducts);

    @Mapping(source = "id", target = "orderId")
    @Mapping(target = "noOfItems", expression = "java(order.getOrderProducts().size())")
    BriefOrderResponse toBriefOrder(Order order);

    List<BriefOrderResponse> toBriefOrders(List<Order> orders);

    @Mapping(source = "id", target = "orderId")
    DetailedOrderResponse toDetailedOrderResponse(Order order);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "status", source = "order")
    @Mapping(target = "type", source = "order.orderType")
    @Mapping(target = "totalPrice", expression = "java(toItemsTotalPrice(items))")
    BriefSellerOrderResponse toBriefSellerOrderResponse(Order order, List<OrderProductResponse> items);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "status", source = "order")
    @Mapping(target = "items", source = "order.orderProducts")
    @Mapping(target = "totalPrice", expression = "java(toItemsTotalPrice(items))")
    DetailedSellerOrderResponse toDetailedSellerOrderResponse(Order order, List<OrderProductResponse> items);

    default CompactedOrderStatus toCompactedStatus(Order order) {
        return switch (order.getStatus()) {
            case CANCELLED -> CompactedOrderStatus.CANCELLED;
            case CONFIRMED -> order.getShippingStatus() != null
                    ? CompactedOrderStatus.valueOf(order.getShippingStatus().name())
                    : CompactedOrderStatus.PENDING;
            default -> CompactedOrderStatus.PENDING;
        };
    }

    default BigDecimal toItemsTotalPrice(List<OrderProductResponse> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
