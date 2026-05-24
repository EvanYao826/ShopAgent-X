package com.demo.aiknowledge.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CartItemVO {
    private Long id;
    private Long userId;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 商品信息
    private Product product;

    // SKU 信息
    private ProductSku sku;

    // 计算总价（优先使用 SKU 价格）
    public BigDecimal getTotalPrice() {
        BigDecimal price = BigDecimal.ZERO;
        if (sku != null && sku.getPrice() != null) {
            price = sku.getPrice();
        } else if (product != null && product.getBasePrice() != null) {
            price = product.getBasePrice();
        }
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
