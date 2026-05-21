package com.demo.aiknowledge.service.impl;

import com.demo.aiknowledge.entity.RecommendationLog;
import com.demo.aiknowledge.mapper.RecommendationLogMapper;
import com.demo.aiknowledge.service.RecommendationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationLogServiceImpl implements RecommendationLogService {

    private final RecommendationLogMapper recommendationLogMapper;

    @Override
    public void save(RecommendationLog log) {
        recommendationLogMapper.insert(log);
    }

    @Override
    public void updateFeedback(Long id, Integer feedback) {
        RecommendationLog log = recommendationLogMapper.selectById(id);
        if (log != null) {
            log.setUserFeedback(feedback);
            recommendationLogMapper.updateById(log);
        }
    }
}
