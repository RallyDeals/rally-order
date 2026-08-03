package com.rally.order.mapper;

import com.rally.order.dto.CheckOutOrderResponse;
import com.rally.order.model.Order;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    CheckOutOrderResponse toCheckoutOrderResponse(Order order);

    List<OrderItem> toOrderItems(List<OrderProduct> orderProducts);
}
