package com.demo.aiknowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.*;
import com.demo.aiknowledge.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductSkuService productSkuService;
    private final ProductImageService productImageService;
    private final ProductReviewService productReviewService;
    private final ProductFaqService productFaqService;

    @GetMapping("/list")
    public Result<IPage<Product>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.success(productService.listByCategory(categoryId, page, size, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            return Result.error("商品不存在");
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("product", product);
        detail.put("skus", productSkuService.listByProductId(id));
        detail.put("images", productImageService.listByProductId(id));
        detail.put("reviews", productReviewService.listByProductId(id, 10));
        detail.put("faqs", productFaqService.listByProductId(id));
        return Result.success(detail);
    }

    @GetMapping("/{id}/skus")
    public Result<List<ProductSku>> skus(@PathVariable Long id) {
        return Result.success(productSkuService.listByProductId(id));
    }

    @GetMapping("/{id}/reviews")
    public Result<List<ProductReview>> reviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(productReviewService.listByProductId(id, limit));
    }

    @PostMapping("/{id}/reviews")
    public Result<ProductReview> submitReview(
            @PathVariable Long id,
            @RequestParam Integer rating,
            @RequestParam String content) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return Result.success(productReviewService.submitReview(userId, id, rating, content));
    }

    @GetMapping("/{id}/faqs")
    public Result<List<ProductFaq>> faqs(@PathVariable Long id) {
        return Result.success(productFaqService.listByProductId(id));
    }

    @GetMapping("/{id}/images")
    public Result<List<ProductImage>> images(@PathVariable Long id) {
        return Result.success(productImageService.listByProductId(id));
    }

    @GetMapping("/search")
    public Result<List<Product>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(productService.search(keyword, limit));
    }

    @GetMapping("/hot")
    public Result<List<Product>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(productService.getHotProducts(limit));
    }
}
