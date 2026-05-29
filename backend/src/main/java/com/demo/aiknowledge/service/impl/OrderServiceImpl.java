package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.dto.CreateOrderRequest;
import com.demo.aiknowledge.entity.*;
import com.demo.aiknowledge.mapper.*;
import com.demo.aiknowledge.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AddressMapper addressMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    @Override
    @Transactional
    public OrderVO createOrder(Long userId, CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("订单商品不能为空");
        }

        // 查询收货地址
        Address address = addressMapper.selectById(request.getAddressId());
        if (address == null || !address.getUserId().equals(userId)) {
            throw new RuntimeException("收货地址无效");
        }

        // 生成订单号
        String orderNo = generateOrderNo();

        // 构建订单
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setStatus(0); // 待付款
        order.setAddressId(address.getId());
        order.setReceiverName(request.getReceiverName() != null && !request.getReceiverName().isBlank()
                ? request.getReceiverName() : address.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone() != null && !request.getReceiverPhone().isBlank()
                ? request.getReceiverPhone() : address.getPhone());
        order.setReceiverAddress(
                address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail());
        order.setRemark(request.getRemark());
        order.setFreightAmount(BigDecimal.ZERO);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        BigDecimal totalAmount = BigDecimal.ZERO;

        // 构建订单项并计算总价
        List<OrderItem> orderItems = new java.util.ArrayList<>();
        for (CreateOrderRequest.OrderItemDto itemDto : request.getItems()) {
            Product product = productMapper.selectById(itemDto.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在: " + itemDto.getProductId());
            }

            BigDecimal price = product.getBasePrice();
            String skuProperties = null;

            if (itemDto.getSkuId() != null) {
                ProductSku sku = productSkuMapper.selectById(itemDto.getSkuId());
                if (sku != null) {
                    price = sku.getPrice();
                    if (sku.getProperties() != null) {
                        skuProperties = sku.getProperties().entrySet().stream()
                                .map(e -> e.getKey() + ": " + e.getValue())
                                .collect(Collectors.joining(" "));
                    }
                }
            }

            int quantity = itemDto.getQuantity() != null && itemDto.getQuantity() > 0 ? itemDto.getQuantity() : 1;
            BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(quantity));

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductTitle(product.getTitle());
            orderItem.setProductImage(product.getImageUrl());
            orderItem.setSkuId(itemDto.getSkuId());
            orderItem.setSkuProperties(skuProperties);
            orderItem.setPrice(price);
            orderItem.setQuantity(quantity);
            orderItem.setTotalAmount(itemTotal);
            orderItems.add(orderItem);

            totalAmount = totalAmount.add(itemTotal);
        }

        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount.add(order.getFreightAmount()));
        orderMapper.insert(order);

        // 插入订单项
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        return getOrderDetail(userId, order.getId());
    }

    @Override
    public IPage<OrderVO> listOrders(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime);
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }

        IPage<Order> orderPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
        if (orderPage.getRecords().isEmpty()) {
            return new Page<>(page, size, 0);
        }

        // 批量查询订单项
        List<Long> orderIds = orderPage.getRecords().stream()
                .map(Order::getId).collect(Collectors.toList());
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        Map<Long, List<OrderItem>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 组装 VO
        Page<OrderVO> voPage = new Page<>(page, size, orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(order -> {
            OrderVO vo = new OrderVO();
            copyOrderToVO(order, vo);
            vo.setItems(itemMap.getOrDefault(order.getId(), List.of()));
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        OrderVO vo = new OrderVO();
        copyOrderToVO(order, vo);
        vo.setItems(items);
        return vo;
    }

    @Override
    @Transactional
    public OrderVO payOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new RuntimeException("订单状态不允许支付");
        }
        order.setStatus(1); // 待发货
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return getOrderDetail(userId, orderId);
    }

    @Override
    @Transactional
    public OrderVO cancelOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new RuntimeException("订单状态不允许取消");
        }
        order.setStatus(4); // 已取消
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return getOrderDetail(userId, orderId);
    }

    @Override
    @Transactional
    public OrderVO confirmReceive(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != 2) {
            throw new RuntimeException("订单状态不允许确认收货");
        }
        order.setStatus(3); // 已完成
        order.setReceiveTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return getOrderDetail(userId, orderId);
    }

    @Override
    @Transactional
    public void deleteOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != 3 && order.getStatus() != 4) {
            throw new RuntimeException("只能删除已完成或已取消的订单");
        }
        orderItemMapper.delete(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        orderMapper.deleteById(orderId);
    }

    private void copyOrderToVO(Order order, OrderVO vo) {
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setStatus(order.getStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setDeliveryTime(order.getDeliveryTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setUpdateTime(order.getUpdateTime());
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return timestamp + random;
    }
}
