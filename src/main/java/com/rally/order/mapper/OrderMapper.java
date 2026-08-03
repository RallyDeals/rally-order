package com.rally.order.mapper;

import com.rally.order.dto.CheckOutOrderResponse;
import com.rally.order.dto.OrderItem;
import com.rally.order.model.Order;
import com.rally.order.model.OrderProduct;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    CheckOutOrderResponse toCheckoutOrderResponse(Order order);

    List<OrderItem> toOrderItems(List<OrderProduct> orderProducts);
}
