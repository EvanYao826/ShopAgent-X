package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.aiknowledge.common.UrlUtil;
import com.demo.aiknowledge.entity.Product;
import com.demo.aiknowledge.entity.UserBrowseHistory;
import com.demo.aiknowledge.mapper.ProductMapper;
import com.demo.aiknowledge.mapper.UserBrowseHistoryMapper;
import com.demo.aiknowledge.service.UserBrowseHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserBrowseHistoryServiceImpl implements UserBrowseHistoryService {

    private final UserBrowseHistoryMapper userBrowseHistoryMapper;
    private final ProductMapper productMapper;
    private final UrlUtil urlUtil;

    @Override
    public void saveOrUpdate(UserBrowseHistory history) {
        UserBrowseHistory existing = userBrowseHistoryMapper.selectOne(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, history.getUserId())
                        .eq(UserBrowseHistory::getProductId, history.getProductId()));
        if (existing != null) {
            existing.setCreateTime(LocalDateTime.now());
            existing.setSource(history.getSource());
            existing.setDurationSec(history.getDurationSec());
            userBrowseHistoryMapper.updateById(existing);
        } else {
            history.setCreateTime(LocalDateTime.now());
            userBrowseHistoryMapper.insert(history);
        }
    }

    @Override
    public List<UserBrowseHistory> listByUserId(Long userId) {
        List<UserBrowseHistory> all = userBrowseHistoryMapper.selectList(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .orderByDesc(UserBrowseHistory::getCreateTime));
        // 去重：同一商品只保留最新一条
        Map<Long, UserBrowseHistory> seen = new LinkedHashMap<>();
        for (UserBrowseHistory h : all) {
            seen.putIfAbsent(h.getProductId(), h);
        }
        List<UserBrowseHistory> histories = new ArrayList<>(seen.values());
        // 批量查询商品信息，避免 N+1
        List<Long> productIds = histories.stream()
                .map(UserBrowseHistory::getProductId)
                .collect(Collectors.toList());
        if (!productIds.isEmpty()) {
            Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));
            for (UserBrowseHistory history : histories) {
                Product product = productMap.get(history.getProductId());
                if (product != null) {
                    history.setProductName(product.getTitle());
                    history.setProductImage(urlUtil.toAbsoluteUrl(product.getImageUrl()));
                    history.setProductPrice(product.getBasePrice());
                }
            }
        }
        return histories;
    }
}
