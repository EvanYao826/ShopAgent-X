package com.demo.aiknowledge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Product;
import com.demo.aiknowledge.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 - 商品管理控制器
 * 提供商品CRUD和上下架
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductMapper productMapper;

    /** 商品列表（分页+品类筛选） */
    @GetMapping("/products")
    public Result<IPage<Product>> listProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(Product::getCreateTime);
        return Result.success(productMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /** 新增商品 */
    @PostMapping("/products")
    public Result<Product> addProduct(@RequestBody Product product) {
        if (product.getTitle() == null || product.getTitle().isEmpty()) {
            return Result.error("商品标题不能为空");
        }
        productMapper.insert(product);
        return Result.success(product);
    }

    /** 编辑商品 */
    @PutMapping("/products/{id}")
    public Result<Product> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            return Result.error("商品不存在");
        }
        product.setId(id);
        productMapper.updateById(product);
        return Result.success(productMapper.selectById(id));
    }

    /** 上下架 */
    @PutMapping("/products/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productMapper.update(null,
                new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, id)
                        .set(Product::getStatus, status));
        return Result.success(null);
    }
}
