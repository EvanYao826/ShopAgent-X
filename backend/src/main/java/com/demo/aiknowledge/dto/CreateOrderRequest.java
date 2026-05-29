package com.demo.aiknowledge.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateOrderRequest {
    private Long addressId;
    private List<OrderItemDto> items;
    private String remark;
    private String receiverName;
    private String receiverPhone;

    @Data
    public static class OrderItemDto {
        private Long productId;
        private Long skuId;
        private Integer quantity;
    }
}
