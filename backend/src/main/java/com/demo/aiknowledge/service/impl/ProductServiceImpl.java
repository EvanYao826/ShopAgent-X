package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.entity.Product;
import com.demo.aiknowledge.mapper.ProductMapper;
import com.demo.aiknowledge.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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

    // 同义词/泛词扩展表
    private static final Map<String, List<String>> SYNONYM_MAP = new HashMap<>();
    static {
        // 服饰大类
        SYNONYM_MAP.put("衣服", Arrays.asList("卫衣", "T恤", "衬衫", "外套", "夹克", "短袖", "长袖"));
        SYNONYM_MAP.put("裤子", Arrays.asList("牛仔裤", "休闲裤", "运动裤", "长裤", "短裤", "工装裤", "西裤"));
        SYNONYM_MAP.put("鞋", Arrays.asList("运动鞋", "跑步鞋", "篮球鞋", "板鞋", "休闲鞋", "帆布鞋"));
        SYNONYM_MAP.put("鞋子", Arrays.asList("运动鞋", "跑步鞋", "篮球鞋", "板鞋", "休闲鞋", "帆布鞋"));
        // 运动鞋细分
        SYNONYM_MAP.put("跑鞋", Arrays.asList("跑步鞋", "运动鞋", "马拉松鞋"));
        SYNONYM_MAP.put("篮球鞋", Arrays.asList("篮球鞋", "高帮球鞋", "实战篮球鞋"));
        // 护肤大类
        SYNONYM_MAP.put("护肤品", Arrays.asList("精华", "面霜", "乳液", "爽肤水", "化妆水", "面膜", "眼霜", "防晒"));
        SYNONYM_MAP.put("化妆品", Arrays.asList("口红", "粉底", "眼影", "腮红", "遮瑕", "气垫", "散粉"));
        SYNONYM_MAP.put("护肤", Arrays.asList("精华", "面霜", "乳液", "爽肤水", "面膜", "眼霜"));
        // 护肤细分
        SYNONYM_MAP.put("面膜", Arrays.asList("面膜", "贴片面膜", "涂抹面膜", "睡眠面膜"));
        SYNONYM_MAP.put("精华", Arrays.asList("精华液", "精华露", "肌底液", "安瓶"));
        SYNONYM_MAP.put("防晒", Arrays.asList("防晒霜", "防晒喷雾", "防晒乳", "隔离霜"));
        // 数码
        SYNONYM_MAP.put("手机", Arrays.asList("手机", "智能手机", "5G手机"));
        SYNONYM_MAP.put("耳机", Arrays.asList("蓝牙耳机", "无线耳机", "头戴耳机", "降噪耳机", "运动耳机"));
        // 食品
        SYNONYM_MAP.put("零食", Arrays.asList("坚果", "饼干", "薯片", "巧克力", "糖果", "肉脯"));
        SYNONYM_MAP.put("饮料", Arrays.asList("气泡水", "果汁", "茶饮", "咖啡", "矿泉水", "苏打水"));
    }

    @Override
    public List<Product> search(String keyword, int limit) {
        // 收集所有搜索词（原词 + 扩展词）
        List<String> terms = new ArrayList<>();
        terms.add(keyword);
        if (SYNONYM_MAP.containsKey(keyword)) {
            terms.addAll(SYNONYM_MAP.get(keyword));
        }

        // 每个词单独搜索，合并去重
        Map<Long, Product> productMap = new LinkedHashMap<>();
        for (String term : terms) {
            List<Product> results = productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .eq(Product::getStatus, 1)
                            .and(w -> w.like(Product::getTitle, term)
                                    .or().like(Product::getBrand, term)
                                    .or().like(Product::getTags, term)
                                    .or().like(Product::getDescription, term))
                            .last("LIMIT 20"));
            for (Product p : results) {
                productMap.putIfAbsent(p.getId(), p);
            }
        }

        // 按相关性排序：title命中 +3，brand/tags +2，description +1，销量加权
        List<Product> sorted = productMap.values().stream()
                .sorted((a, b) -> {
                    int sa = calcScore(a, keyword);
                    int sb = calcScore(b, keyword);
                    if (sb != sa) return sb - sa;
                    int salesA = a.getSalesCount() != null ? a.getSalesCount() : 0;
                    int salesB = b.getSalesCount() != null ? b.getSalesCount() : 0;
                    return salesB - salesA;
                })
                .limit(limit)
                .collect(Collectors.toList());

        return sorted;
    }

    private int calcScore(Product p, String keyword) {
        int score = 0;
        if (p.getTitle() != null && p.getTitle().contains(keyword)) score += 3;
        if (p.getBrand() != null && p.getBrand().contains(keyword)) score += 2;
        if (p.getTags() != null && p.getTags().contains(keyword)) score += 2;
        if (p.getDescription() != null && p.getDescription().contains(keyword)) score += 1;
        return score;
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
