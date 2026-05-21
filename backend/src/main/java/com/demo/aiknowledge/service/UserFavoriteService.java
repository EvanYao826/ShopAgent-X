package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.UserFavorite;
import java.util.List;

public interface UserFavoriteService {
    void addFavorite(Long userId, Long productId);
    void removeFavorite(Long userId, Long productId);
    List<UserFavorite> listByUserId(Long userId);
}
