package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.ProductReview;
import java.util.List;

public interface ProductReviewService {
    List<ProductReview> listByProductId(Long productId, int limit);
    ProductReview submitReview(Long userId, Long productId, Integer rating, String content);
}
