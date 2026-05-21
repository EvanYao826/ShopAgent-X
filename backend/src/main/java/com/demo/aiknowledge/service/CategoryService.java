package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.Category;
import java.util.List;

public interface CategoryService {
    List<Category> listActive();
    Category getById(Long id);
}
