package com.rally.order.mapper;

import com.rally.order.dto.BriefOrderResponse;
import com.rally.order.dto.CheckOutOrderResponse;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.dto.OrderItem;
import com.rally.order.model.Order;
import com.rally.order.model.OrderProduct;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    CheckOutOrderResponse toCheckoutOrderResponse(Order order);

    List<OrderItem> toOrderItems(List<OrderProduct> orderProducts);

    @Mapping(source = "id", target = "orderId")
    @Mapping(target = "noOfItems", expression = "java(order.getOrderProducts().size())")
    BriefOrderResponse toBriefOrder(Order order);

    List<BriefOrderResponse> toBriefOrders(List<Order> orders);

    @Mapping(source = "id", target = "orderId")
    DetailedOrderResponse toDetailedOrderResponse(Order order);
}
