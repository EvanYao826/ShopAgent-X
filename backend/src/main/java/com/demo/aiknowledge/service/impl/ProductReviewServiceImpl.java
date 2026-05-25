package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.aiknowledge.entity.ProductReview;
import com.demo.aiknowledge.mapper.ProductReviewMapper;
import com.demo.aiknowledge.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    @Override
    public ProductReview submitReview(Long userId, Long productId, Integer rating, String content) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在1-5之间");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("评价内容不能为空");
        }
        ProductReview review = new ProductReview();
        review.setUserId(userId);
        review.setProductId(productId);
        review.setRating(rating);
        review.setContent(content.trim());
        review.setCreateTime(LocalDateTime.now());
        productReviewMapper.insert(review);
        return review;
    }
}
