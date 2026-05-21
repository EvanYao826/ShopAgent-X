package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.ProductFaq;
import java.util.List;

public interface ProductFaqService {
    List<ProductFaq> listByProductId(Long productId);
}
