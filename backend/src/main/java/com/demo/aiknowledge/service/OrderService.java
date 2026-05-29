package com.demo.aiknowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.demo.aiknowledge.dto.CreateOrderRequest;
import com.demo.aiknowledge.entity.OrderVO;

public interface OrderService {
    OrderVO createOrder(Long userId, CreateOrderRequest request);
    IPage<OrderVO> listOrders(Long userId, Integer status, int page, int size);
    OrderVO getOrderDetail(Long userId, Long orderId);
    OrderVO payOrder(Long userId, Long orderId);
    OrderVO cancelOrder(Long userId, Long orderId);
    OrderVO confirmReceive(Long userId, Long orderId);
    void deleteOrder(Long userId, Long orderId);
}
