package com.demo.aiknowledge.controller;

import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Cart;
import com.demo.aiknowledge.entity.CartItemVO;
import com.demo.aiknowledge.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/add")
    public Result<Cart> add(@RequestParam Long userId, @RequestParam Long productId,
                            @RequestParam(required = false) Long skuId) {
        return Result.success(cartService.addItem(userId, productId, skuId, 1));
    }

    @DeleteMapping("/remove")
    public Result<Void> remove(@RequestParam Long userId, @RequestParam Long productId) {
        cartService.removeItem(userId, productId);
        return Result.success(null);
    }

    @PutMapping("/update")
    public Result<Cart> update(
            @RequestParam Long userId,
            @RequestParam Long productId,
            @RequestParam Integer quantity) {
        cartService.updateQuantity(userId, productId, quantity);
        return Result.success(cartService.getByUserIdAndProductId(userId, productId));
    }

    @PutMapping("/updateSku")
    public Result<Void> updateSku(
            @RequestParam Long userId,
            @RequestParam Long productId,
            @RequestParam Long oldSkuId,
            @RequestParam Long newSkuId) {
        cartService.updateSku(userId, productId, oldSkuId, newSkuId);
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<List<CartItemVO>> list(@RequestParam Long userId) {
        return Result.success(cartService.listWithProductByUserId(userId));
    }
}
