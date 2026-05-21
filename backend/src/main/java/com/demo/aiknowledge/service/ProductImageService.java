package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.ProductImage;
import java.util.List;

public interface ProductImageService {
    List<ProductImage> listByProductId(Long productId);
}
