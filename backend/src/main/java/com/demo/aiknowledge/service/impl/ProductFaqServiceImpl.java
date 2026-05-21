package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.aiknowledge.entity.ProductFaq;
import com.demo.aiknowledge.mapper.ProductFaqMapper;
import com.demo.aiknowledge.service.ProductFaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductFaqServiceImpl implements ProductFaqService {

    private final ProductFaqMapper productFaqMapper;

    @Override
    public List<ProductFaq> listByProductId(Long productId) {
        return productFaqMapper.selectList(
                new LambdaQueryWrapper<ProductFaq>()
                        .eq(ProductFaq::getProductId, productId)
                        .orderByAsc(ProductFaq::getSortOrder));
    }
}
