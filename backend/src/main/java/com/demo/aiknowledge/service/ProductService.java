package com.demo.aiknowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.demo.aiknowledge.entity.Product;
import java.util.List;

public interface ProductService {
    IPage<Product> listByCategory(Long categoryId, int page, int size);
    Product getById(Long id);
    List<Product> search(String keyword, int limit);
    List<Product> getHotProducts(int limit);
}
