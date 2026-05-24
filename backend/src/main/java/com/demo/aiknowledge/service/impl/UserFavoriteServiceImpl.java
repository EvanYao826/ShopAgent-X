package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.aiknowledge.entity.Product;
import com.demo.aiknowledge.entity.UserFavorite;
import com.demo.aiknowledge.mapper.ProductMapper;
import com.demo.aiknowledge.mapper.UserFavoriteMapper;
import com.demo.aiknowledge.service.UserFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserFavoriteServiceImpl implements UserFavoriteService {

    private final UserFavoriteMapper userFavoriteMapper;
    private final ProductMapper productMapper;

    @Override
    public void addFavorite(Long userId, Long productId) {
        UserFavorite existing = userFavoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getProductId, productId));
        if (existing != null) {
            return;
        }
        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId);
        fav.setProductId(productId);
        fav.setCreateTime(LocalDateTime.now());
        userFavoriteMapper.insert(fav);
    }

    @Override
    public void removeFavorite(Long userId, Long productId) {
        userFavoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getProductId, productId));
    }

    @Override
    public List<UserFavorite> listByUserId(Long userId) {
        List<UserFavorite> favorites = userFavoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreateTime));
        // 批量查询商品信息，避免 N+1
        List<Long> productIds = favorites.stream()
                .map(UserFavorite::getProductId)
                .collect(java.util.stream.Collectors.toList());
        if (!productIds.isEmpty()) {
            Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                    .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
            for (UserFavorite fav : favorites) {
                Product product = productMap.get(fav.getProductId());
                if (product != null) {
                    fav.setProductName(product.getTitle());
                    fav.setProductImage(product.getImageUrl());
                    fav.setProductPrice(product.getBasePrice());
                }
            }
        }
        return favorites;
    }
}
