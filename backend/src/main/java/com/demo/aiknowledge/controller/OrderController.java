package com.demo.aiknowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.dto.CreateOrderRequest;
import com.demo.aiknowledge.entity.OrderVO;
import com.demo.aiknowledge.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PostMapping("/create")
    public Result<OrderVO> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            return Result.success(orderService.createOrder(getCurrentUserId(), request));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<IPage<OrderVO>> listOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.listOrders(getCurrentUserId(), status, page, size));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long id) {
        try {
            return Result.success(orderService.getOrderDetail(getCurrentUserId(), id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/pay")
    public Result<OrderVO> payOrder(@PathVariable Long id) {
        try {
            return Result.success(orderService.payOrder(getCurrentUserId(), id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/cancel")
    public Result<OrderVO> cancelOrder(@PathVariable Long id) {
        try {
            return Result.success(orderService.cancelOrder(getCurrentUserId(), id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/receive")
    public Result<OrderVO> confirmReceive(@PathVariable Long id) {
        try {
            return Result.success(orderService.confirmReceive(getCurrentUserId(), id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteOrder(@PathVariable Long id) {
        try {
            orderService.deleteOrder(getCurrentUserId(), id);
            return Result.success("订单已删除");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
