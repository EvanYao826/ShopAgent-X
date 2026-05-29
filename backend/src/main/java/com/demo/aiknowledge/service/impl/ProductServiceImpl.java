package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.entity.Product;
import com.demo.aiknowledge.mapper.ProductMapper;
import com.demo.aiknowledge.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    @Override
    public IPage<Product> listByCategory(Long categoryId, int page, int size, String sortBy, String sortOrder) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }

        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortBy != null ? sortBy : "sales") {
            case "price":
                wrapper.orderBy(true, isAsc, Product::getBasePrice);
                break;
            case "rating":
                wrapper.orderBy(true, isAsc, Product::getRating);
                break;
            case "reviews":
                wrapper.orderBy(true, isAsc, Product::getReviewCount);
                break;
            case "newest":
                wrapper.orderBy(true, isAsc, Product::getCreateTime);
                break;
            default:
                wrapper.orderByDesc(Product::getSalesCount);
                break;
        }

        return productMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public Product getById(Long id) {
        return productMapper.selectById(id);
    }

    @Override
    public List<Product> search(String keyword, int limit) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .and(w -> w.like(Product::getTitle, keyword)
                        .or().like(Product::getBrand, keyword)
                        .or().like(Product::getTags, keyword)
                        .or().like(Product::getDescription, keyword))
                .orderByDesc(Product::getRating)
                .last("LIMIT " + limit);
        return productMapper.selectList(wrapper);
    }

    @Override
    public List<Product> getHotProducts(int limit) {
        return productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)
                        .orderByDesc(Product::getSalesCount)
                        .last("LIMIT " + limit));
    }
}
