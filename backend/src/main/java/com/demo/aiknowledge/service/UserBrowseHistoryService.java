package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.UserBrowseHistory;

import java.util.List;

public interface UserBrowseHistoryService {
    void save(UserBrowseHistory history);
    List<UserBrowseHistory> listByUserId(Long userId);
}
