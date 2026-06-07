package com.demo.aiknowledge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Order;
import com.demo.aiknowledge.entity.OrderItem;
import com.demo.aiknowledge.mapper.OrderItemMapper;
import com.demo.aiknowledge.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin 订单管理控制器 —— 查看/搜索/统计订单
 */
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @GetMapping("/list")
    public Result<IPage<Order>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String keyword) {
        QueryWrapper<Order> qw = new QueryWrapper<>();
        if (userId != null) qw.eq("user_id", userId);
        if (status != null) qw.eq("status", status);
        if (orderNo != null && !orderNo.isEmpty()) qw.like("order_no", orderNo);
        if (keyword != null && !keyword.isEmpty())
            qw.and(w -> w.like("receiver_name", keyword).or().like("receiver_phone", keyword));
        qw.orderByDesc("create_time");
        return Result.success(orderMapper.selectPage(new Page<>(page, size), qw));
    }

    @GetMapping("/{id}/items")
    public Result<List<OrderItem>> items(@PathVariable Long id) {
        return Result.success(orderItemMapper.selectList(
            new QueryWrapper<OrderItem>().eq("order_id", id)));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", orderMapper.selectCount(null));
        stats.put("pendingPayment", orderMapper.selectCount(new QueryWrapper<Order>().eq("status", 0)));
        stats.put("pendingDelivery", orderMapper.selectCount(new QueryWrapper<Order>().eq("status", 1)));
        stats.put("delivered", orderMapper.selectCount(new QueryWrapper<Order>().eq("status", 2)));
        stats.put("completed", orderMapper.selectCount(new QueryWrapper<Order>().eq("status", 3)));
        stats.put("cancelled", orderMapper.selectCount(new QueryWrapper<Order>().eq("status", 4)));

        // 今日订单
        stats.put("todayOrders", orderMapper.selectCount(new QueryWrapper<Order>()
            .ge("create_time", java.time.LocalDate.now().atStartOfDay())));

        // 总销售额 (已支付+已完成+已发货)
        stats.put("totalRevenue", orderMapper.selectList(
            new QueryWrapper<Order>().in("status", 1, 2, 3))
            .stream().mapToDouble(o -> o.getPayAmount() != null ? o.getPayAmount().doubleValue() : 0).sum());

        return Result.success(stats);
    }
}
