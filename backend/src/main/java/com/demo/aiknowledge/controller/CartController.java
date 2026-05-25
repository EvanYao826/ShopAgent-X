package com.demo.aiknowledge.controller;

import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Cart;
import com.demo.aiknowledge.entity.CartItemVO;
import com.demo.aiknowledge.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PostMapping("/add")
    public Result<Cart> add(@RequestParam Long productId,
                            @RequestParam(required = false) Long skuId) {
        return Result.success(cartService.addItem(getCurrentUserId(), productId, skuId, 1));
    }

    @DeleteMapping("/remove")
    public Result<Void> remove(@RequestParam Long productId) {
        cartService.removeItem(getCurrentUserId(), productId);
        return Result.success(null);
    }

    @PutMapping("/update")
    public Result<Cart> update(
            @RequestParam Long productId,
            @RequestParam Integer quantity) {
        Long userId = getCurrentUserId();
        cartService.updateQuantity(userId, productId, quantity);
        return Result.success(cartService.getByUserIdAndProductId(userId, productId));
    }

    @PutMapping("/updateSku")
    public Result<Void> updateSku(
            @RequestParam Long productId,
            @RequestParam Long oldSkuId,
            @RequestParam Long newSkuId) {
        cartService.updateSku(getCurrentUserId(), productId, oldSkuId, newSkuId);
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<List<CartItemVO>> list() {
        return Result.success(cartService.listWithProductByUserId(getCurrentUserId()));
    }

    @GetMapping("/count")
    public Result<Integer> count() {
        return Result.success(cartService.getCount(getCurrentUserId()));
    }

    @PostMapping("/checkAll")
    public Result<Void> checkAll(@RequestParam boolean checked) {
        cartService.checkAll(getCurrentUserId(), checked);
        return Result.success(null);
    }
}
