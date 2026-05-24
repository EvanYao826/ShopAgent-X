package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.aiknowledge.entity.Cart;
import com.demo.aiknowledge.entity.CartItemVO;
import com.demo.aiknowledge.entity.Product;
import com.demo.aiknowledge.entity.ProductSku;
import com.demo.aiknowledge.mapper.CartMapper;
import com.demo.aiknowledge.mapper.ProductMapper;
import com.demo.aiknowledge.mapper.ProductSkuMapper;
import com.demo.aiknowledge.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    @Override
    @Transactional
    public Cart addItem(Long userId, Long productId, Long skuId, Integer quantity) {
        // 查找是否已存在相同商品和 SKU 的购物车项
        Cart existing = cartMapper.selectOne(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getProductId, productId)
                        .eq(Cart::getSkuId, skuId));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            cartMapper.updateById(existing);
            return existing;
        }
        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setSkuId(skuId);
        cart.setQuantity(quantity);
        cart.setCreateTime(LocalDateTime.now());
        cart.setUpdateTime(LocalDateTime.now());
        cartMapper.insert(cart);
        return cart;
    }

    @Override
    @Transactional
    public void removeItem(Long userId, Long productId) {
        cartMapper.delete(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getProductId, productId));
    }

    @Override
    @Transactional
    public void updateQuantity(Long userId, Long productId, Integer quantity) {
        Cart cart = new Cart();
        cart.setQuantity(quantity);
        cartMapper.update(cart,
                new LambdaUpdateWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getProductId, productId));
    }

    @Override
    @Transactional
    public void updateSku(Long userId, Long productId, Long oldSkuId, Long newSkuId) {
        // 查找原购物车项
        Cart existing = cartMapper.selectOne(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getProductId, productId)
                        .eq(Cart::getSkuId, oldSkuId));
        if (existing == null) {
            return;
        }

        // 检查新 SKU 是否已存在
        Cart existingNew = cartMapper.selectOne(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getProductId, productId)
                        .eq(Cart::getSkuId, newSkuId));
        if (existingNew != null) {
            // 合并数量，删除原项
            existingNew.setQuantity(existingNew.getQuantity() + existing.getQuantity());
            cartMapper.updateById(existingNew);
            cartMapper.deleteById(existing.getId());
        } else {
            // 更新 SKU
            existing.setSkuId(newSkuId);
            existing.setUpdateTime(LocalDateTime.now());
            cartMapper.updateById(existing);
        }
    }

    @Override
    public List<Cart> listByUserId(Long userId) {
        return cartMapper.selectList(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .orderByDesc(Cart::getCreateTime));
    }

    @Override
    public Cart getByUserIdAndProductId(Long userId, Long productId) {
        return cartMapper.selectOne(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getProductId, productId));
    }

    @Override
    public List<CartItemVO> listWithProductByUserId(Long userId) {
        List<Cart> cartList = listByUserId(userId);
        if (cartList.isEmpty()) {
            return List.of();
        }

        // 批量查询商品信息
        List<Long> productIds = cartList.stream()
                .map(Cart::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 批量查询 SKU 信息
        List<Long> skuIds = cartList.stream()
                .map(Cart::getSkuId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ProductSku> skuMap = skuIds.isEmpty() ? Map.of() :
                productSkuMapper.selectBatchIds(skuIds)
                        .stream()
                        .collect(Collectors.toMap(ProductSku::getId, s -> s));

        // 组装 CartItemVO
        return cartList.stream().map(cart -> {
            CartItemVO vo = new CartItemVO();
            vo.setId(cart.getId());
            vo.setUserId(cart.getUserId());
            vo.setProductId(cart.getProductId());
            vo.setSkuId(cart.getSkuId());
            vo.setQuantity(cart.getQuantity());
            vo.setCreateTime(cart.getCreateTime());
            vo.setUpdateTime(cart.getUpdateTime());
            vo.setProduct(productMap.get(cart.getProductId()));
            if (cart.getSkuId() != null) {
                vo.setSku(skuMap.get(cart.getSkuId()));
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
