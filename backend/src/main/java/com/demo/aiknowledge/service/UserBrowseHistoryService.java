package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.UserBrowseHistory;

import java.util.List;

public interface UserBrowseHistoryService {
    void saveOrUpdate(UserBrowseHistory history);
    List<UserBrowseHistory> listByUserId(Long userId);
    void deleteHistory(Long userId, Long historyId);
}
