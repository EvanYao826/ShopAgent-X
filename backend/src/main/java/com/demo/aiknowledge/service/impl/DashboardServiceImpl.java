package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.demo.aiknowledge.dto.DashboardStats;
import com.demo.aiknowledge.entity.AgentRun;
import com.demo.aiknowledge.entity.Conversation;
import com.demo.aiknowledge.entity.KnowledgeDoc;
import com.demo.aiknowledge.entity.Product;
import com.demo.aiknowledge.entity.QaLog;
import com.demo.aiknowledge.entity.QaUnanswered;
import com.demo.aiknowledge.entity.RecommendationLog;
import com.demo.aiknowledge.entity.User;
import com.demo.aiknowledge.mapper.AgentRunMapper;
import com.demo.aiknowledge.mapper.ConversationMapper;
import com.demo.aiknowledge.mapper.KnowledgeDocMapper;
import com.demo.aiknowledge.mapper.ProductMapper;
import com.demo.aiknowledge.mapper.QaLogMapper;
import com.demo.aiknowledge.mapper.QaUnansweredMapper;
import com.demo.aiknowledge.mapper.RecommendationLogMapper;
import com.demo.aiknowledge.mapper.UserMapper;
import com.demo.aiknowledge.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private KnowledgeDocMapper docMapper;
    @Autowired
    private QaLogMapper qaLogMapper;
    @Autowired
    private QaUnansweredMapper qaUnansweredMapper;
    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private RecommendationLogMapper recommendationLogMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private AgentRunMapper agentRunMapper;

    @Override
    public DashboardStats getStats() {
        DashboardStats stats = new DashboardStats();

        stats.setUserCount(userMapper.selectCount(new QueryWrapper<User>()));
        stats.setDocCount(docMapper.selectCount(new QueryWrapper<KnowledgeDoc>()));

        long totalQa = qaLogMapper.selectCount(new QueryWrapper<QaLog>());
        stats.setQaCount(totalQa);
        stats.setHitRate(calculateHitRate(totalQa));
        stats.setUnansweredQuestions(getTopUnansweredQuestions());
        stats.setQuestionTrends(getQuestionTrends());

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        stats.setTodayConversations(countTodayConversations(todayStart));
        stats.setTodayRecommendations(countTodayRecommendations(todayStart));
        stats.setClickRate(calculateClickRate());
        stats.setSatisfactionRate(calculateSatisfactionRate());
        stats.setIntentDistribution(getIntentDistribution());
        stats.setDailyTrends(getDailyTrends());
        stats.setHotProducts(getHotProducts());
        stats.setRecentRecommendations(getRecentRecommendations());

        return stats;
    }

    private Double calculateHitRate(long totalQa) {
        if (totalQa == 0) {
            return 0.0;
        }

        QueryWrapper<QaUnanswered> unansweredQuery = new QueryWrapper<>();
        unansweredQuery.select("sum(count)");
        List<Object> sumResult = qaUnansweredMapper.selectObjs(unansweredQuery);
        long unansweredTotal = 0;
        if (sumResult != null && !sumResult.isEmpty() && sumResult.get(0) != null) {
            unansweredTotal = Long.parseLong(sumResult.get(0).toString());
        }

        long answered = Math.max(0, totalQa - unansweredTotal);
        return (double) answered / totalQa * 100;
    }

    private List<Map<String, Object>> getTopUnansweredQuestions() {
        QueryWrapper<QaUnanswered> unansweredWrapper = new QueryWrapper<>();
        unansweredWrapper.orderByDesc("count").last("limit 5");
        List<QaUnanswered> unansweredList = qaUnansweredMapper.selectList(unansweredWrapper);

        List<Map<String, Object>> unansweredMaps = new ArrayList<>();
        for (QaUnanswered unanswered : unansweredList) {
            Map<String, Object> item = new HashMap<>();
            item.put("question", unanswered.getQuestion());
            item.put("count", unanswered.getCount());
            unansweredMaps.add(item);
        }
        return unansweredMaps;
    }

    private List<Map<String, Object>> getQuestionTrends() {
        QueryWrapper<QaLog> trendWrapper = new QueryWrapper<>();
        trendWrapper.select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                .ge("create_time", LocalDateTime.now().minusDays(7))
                .groupBy("date")
                .orderByAsc("date");
        return qaLogMapper.selectMaps(trendWrapper);
    }

    private Long countTodayConversations(LocalDateTime todayStart) {
        QueryWrapper<Conversation> todayConvWrapper = new QueryWrapper<>();
        todayConvWrapper.ge("create_time", todayStart);
        return conversationMapper.selectCount(todayConvWrapper);
    }

    private Long countTodayRecommendations(LocalDateTime todayStart) {
        QueryWrapper<RecommendationLog> todayRecWrapper = new QueryWrapper<>();
        todayRecWrapper.ge("create_time", todayStart);
        return recommendationLogMapper.selectCount(todayRecWrapper);
    }

    private Double calculateClickRate() {
        long totalRecs = recommendationLogMapper.selectCount(new QueryWrapper<>());
        if (totalRecs == 0) {
            return 0.0;
        }

        QueryWrapper<RecommendationLog> clickedWrapper = new QueryWrapper<>();
        clickedWrapper.eq("user_clicked", true);
        long clickedCount = recommendationLogMapper.selectCount(clickedWrapper);
        return (double) clickedCount / totalRecs * 100;
    }

    private Double calculateSatisfactionRate() {
        QueryWrapper<RecommendationLog> feedbackWrapper = new QueryWrapper<>();
        feedbackWrapper.isNotNull("user_feedback");
        long feedbackCount = recommendationLogMapper.selectCount(feedbackWrapper);
        if (feedbackCount == 0) {
            return 0.0;
        }

        QueryWrapper<RecommendationLog> satisfiedWrapper = new QueryWrapper<>();
        satisfiedWrapper.eq("user_feedback", 1);
        long satisfiedCount = recommendationLogMapper.selectCount(satisfiedWrapper);
        return (double) satisfiedCount / feedbackCount * 100;
    }

    private Map<String, Long> getIntentDistribution() {
        QueryWrapper<AgentRun> intentWrapper = new QueryWrapper<>();
        intentWrapper.select("intent", "count(*) as count")
                .isNotNull("intent")
                .groupBy("intent");
        List<Map<String, Object>> intentList = agentRunMapper.selectMaps(intentWrapper);

        Map<String, Long> intentMap = new HashMap<>();
        for (Map<String, Object> item : intentList) {
            Object intentValue = item.get("intent");
            Object countValue = item.get("count");
            if (intentValue != null && countValue instanceof Number) {
                intentMap.put(intentValue.toString(), ((Number) countValue).longValue());
            }
        }
        return intentMap;
    }

    private List<Map<String, Object>> getDailyTrends() {
        QueryWrapper<Conversation> trendWrapper = new QueryWrapper<>();
        trendWrapper.select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                .ge("create_time", LocalDateTime.now().minusDays(7))
                .groupBy("date")
                .orderByAsc("date");
        return conversationMapper.selectMaps(trendWrapper);
    }

    private List<Map<String, Object>> getHotProducts() {
        QueryWrapper<Product> hotWrapper = new QueryWrapper<>();
        hotWrapper.select("title", "brand", "base_price", "image_url", "sales_count", "rating")
                .orderByDesc("sales_count")
                .last("limit 5");
        return productMapper.selectMaps(hotWrapper);
    }

    private List<Map<String, Object>> getRecentRecommendations() {
        QueryWrapper<RecommendationLog> recentWrapper = new QueryWrapper<>();
        recentWrapper.orderByDesc("create_time").last("limit 5");
        List<RecommendationLog> recentList = recommendationLogMapper.selectList(recentWrapper);

        List<Map<String, Object>> recentMaps = new ArrayList<>();
        for (RecommendationLog log : recentList) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("query", log.getQuery());
            item.put("intent", log.getIntent());
            item.put("recommendedProductIds", log.getRecommendedProductIds());
            item.put("userClicked", log.getUserClicked());
            item.put("userFeedback", log.getUserFeedback());
            item.put("createTime", log.getCreateTime());
            recentMaps.add(item);
        }
        return recentMaps;
    }
}
