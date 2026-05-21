package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.RecommendationLog;

public interface RecommendationLogService {
    void save(RecommendationLog log);
    void updateFeedback(Long id, Integer feedback);
}
