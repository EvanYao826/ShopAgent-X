package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.aiknowledge.entity.ProductReview;
import com.demo.aiknowledge.mapper.ProductReviewMapper;
import com.demo.aiknowledge.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private final ProductReviewMapper productReviewMapper;

    @Override
    public List<ProductReview> listByProductId(Long productId, int limit) {
        return productReviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getProductId, productId)
                        .orderByDesc(ProductReview::getCreateTime)
                        .last("LIMIT " + limit));
    }
}
