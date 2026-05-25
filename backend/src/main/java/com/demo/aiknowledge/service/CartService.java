package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.Cart;
import com.demo.aiknowledge.entity.CartItemVO;
import java.util.List;

public interface CartService {
    Cart addItem(Long userId, Long productId, Long skuId, Integer quantity);
    void removeItem(Long userId, Long productId);
    void updateQuantity(Long userId, Long productId, Integer quantity);
    void updateSku(Long userId, Long productId, Long oldSkuId, Long newSkuId);
    List<Cart> listByUserId(Long userId);
    List<CartItemVO> listWithProductByUserId(Long userId);
    Cart getByUserIdAndProductId(Long userId, Long productId);
    int getCount(Long userId);
    void checkAll(Long userId, boolean checked);
}
