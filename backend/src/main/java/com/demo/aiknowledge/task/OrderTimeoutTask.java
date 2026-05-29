package com.demo.aiknowledge.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.aiknowledge.entity.Order;
import com.demo.aiknowledge.entity.OrderItem;
import com.demo.aiknowledge.mapper.OrderItemMapper;
import com.demo.aiknowledge.mapper.OrderMapper;
import com.demo.aiknowledge.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper productSkuMapper;

    private static final int PAY_TIMEOUT_MINUTES = 10;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(PAY_TIMEOUT_MINUTES);

        // 查询超时的待付款订单
        List<Order> expiredOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, 0)
                        .le(Order::getCreateTime, deadline)
                        .select(Order::getId));

        if (expiredOrders.isEmpty()) return;

        List<Long> orderIds = expiredOrders.stream().map(Order::getId).toList();

        // 恢复库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        for (OrderItem item : items) {
            if (item.getSkuId() != null) {
                productSkuMapper.updateStock(item.getSkuId(), item.getQuantity());
            }
        }

        // 批量取消订单
        int count = orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .in(Order::getId, orderIds)
                        .eq(Order::getStatus, 0)
                        .set(Order::getStatus, 4)
                        .set(Order::getUpdateTime, LocalDateTime.now()));
        if (count > 0) {
            log.info("自动取消超时未支付订单 {} 个", count);
        }
    }
}
