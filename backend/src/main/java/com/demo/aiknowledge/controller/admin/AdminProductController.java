package com.demo.aiknowledge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Product;
import com.demo.aiknowledge.mapper.ProductMapper;
import com.demo.aiknowledge.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin 商品管理控制器 —— 商品CRUD、上下架、搜索筛选、统计
 */
@RestController
@RequestMapping("/api/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    /**
     * 分页查询商品列表，支持多条件筛选
     */
    @GetMapping("/list")
    public Result<IPage<Product>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        QueryWrapper<Product> qw = new QueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("title", keyword).or().like("brand", keyword).or().like("tags", keyword));
        }
        if (categoryId != null) qw.eq("category_id", categoryId);
        if (brand != null && !brand.isEmpty()) qw.eq("brand", brand);
        if (status != null) qw.eq("status", status);
        if (minPrice != null) qw.ge("base_price", minPrice);
        if (maxPrice != null) qw.le("base_price", maxPrice);

        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        qw.orderBy(true, isAsc, sortBy);

        return Result.success(productMapper.selectPage(new Page<>(page, size), qw));
    }

    /**
     * 商品上下架
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam int status) {
        Product product = productService.getById(id);
        if (product == null) return Result.error("商品不存在");
        product.setStatus(status);
        productMapper.updateById(product);
        return Result.success(null);
    }

    /**
     * 更新商品信息
     */
    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        productMapper.updateById(product);
        return Result.success(productService.getById(id));
    }

    /**
     * 商品统计概览
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", productMapper.selectCount(null));
        stats.put("online", productMapper.selectCount(new QueryWrapper<Product>().eq("status", 1)));
        stats.put("offline", productMapper.selectCount(new QueryWrapper<Product>().eq("status", 0)));
        stats.put("lowStock", productMapper.selectCount(new QueryWrapper<Product>().lt("stock", 10).gt("stock", 0)));
        stats.put("outOfStock", productMapper.selectCount(new QueryWrapper<Product>().eq("stock", 0)));

        // 品牌分布
        QueryWrapper<Product> brandQw = new QueryWrapper<>();
        brandQw.select("brand, count(*) as count").groupBy("brand").orderByDesc("count").last("limit 10");
        stats.put("brandDistribution", productMapper.selectMaps(brandQw));

        // 品类分布
        QueryWrapper<Product> catQw = new QueryWrapper<>();
        catQw.select("category_id, count(*) as count").groupBy("category_id").orderByDesc("count").last("limit 10");
        stats.put("categoryDistribution", productMapper.selectMaps(catQw));

        return Result.success(stats);
    }

    /**
     * 获取所有品牌列表（用于筛选下拉）
     */
    @GetMapping("/brands")
    public Result<java.util.List<String>> brands() {
        QueryWrapper<Product> qw = new QueryWrapper<>();
        qw.select("distinct brand").isNotNull("brand").ne("brand", "");
        return Result.success(productMapper.selectObjs(qw).stream().map(Object::toString).toList());
    }
}
