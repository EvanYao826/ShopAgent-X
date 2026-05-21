package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.ProductSku;
import java.util.List;

public interface ProductSkuService {
    List<ProductSku> listByProductId(Long productId);
}
