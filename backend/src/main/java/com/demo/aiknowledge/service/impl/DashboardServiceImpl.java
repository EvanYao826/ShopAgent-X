package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.demo.aiknowledge.dto.DashboardStats;
import com.demo.aiknowledge.entity.*;
import com.demo.aiknowledge.mapper.*;
import com.demo.aiknowledge.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    // ===== 原有Mapper =====
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private KnowledgeDocMapper docMapper;
    @Autowired
    private QaLogMapper qaLogMapper;
    @Autowired
    private QaUnansweredMapper qaUnansweredMapper;

    // ===== 新增Mapper（电商导购相关）=====
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

        // ===== 保留原有统计（兼容） =====
        stats.setUserCount(userMapper.selectCount(new QueryWrapper<User>()));
        stats.setDocCount(docMapper.selectCount(new QueryWrapper<KnowledgeDoc>()));
        long totalQa = qaLogMapper.selectCount(new QueryWrapper<QaLog>());
        stats.setQaCount(totalQa);

        // Calculate Hit Rate
        QueryWrapper<QaUnanswered> unansweredQuery = new QueryWrapper<>();
        unansweredQuery.select("sum(count)");
        List<Object> sumResult = qaUnansweredMapper.selectObjs(unansweredQuery);
        long unansweredTotal = 0;
        if (sumResult != null && !sumResult.isEmpty() && sumResult.get(0) != null) {
            unansweredTotal = Long.parseLong(sumResult.get(0).toString());
        }
        if (totalQa > 0) {
            long answered = Math.max(0, totalQa - unansweredTotal);
            double rate = (double) answered / totalQa;
            stats.setHitRate(rate * 100);
        } else {
            stats.setHitRate(0.0);
        }

        // Top Questions
        QueryWrapper<QaLog> topQaWrapper = new QueryWrapper<>();
        topQaWrapper.select("question", "count(*) as count")
                .groupBy("question")
                .orderByDesc("count")
                .last("limit 5");
        stats.setTopQuestions(qaLogMapper.selectMaps(topQaWrapper));

        // Unanswered Questions
        QueryWrapper<QaUnanswered> unansweredWrapper = new QueryWrapper<>();
        unansweredWrapper.orderByDesc("count").last("limit 5");
        List<QaUnanswered> unansweredList = qaUnansweredMapper.selectList(unansweredWrapper);
        List<Map<String, Object>> unansweredMaps = new ArrayList<>();
        for (QaUnanswered u : unansweredList) {
            Map<String, Object> m = new HashMap<>();
            m.put("question", u.getQuestion());
            m.put("count", u.getCount());
            unansweredMaps.add(m);
        }
        stats.setUnansweredQuestions(unansweredMaps);

        // Question Trends
        QueryWrapper<QaLog> trendWrapper = new QueryWrapper<>();
        trendWrapper.select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                .ge("create_time", LocalDateTime.now().minusDays(7))
                .groupBy("date")
                .orderByAsc("date");
        stats.setQuestionTrends(qaLogMapper.selectMaps(trendWrapper));

        // ===== 新增：电商导购指标 =====

        // 1. 今日对话数
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        QueryWrapper<Conversation> todayConvWrapper = new QueryWrapper<>();
        todayConvWrapper.ge("create_time", todayStart);
        stats.setTodayConversations(conversationMapper.selectCount(todayConvWrapper));

        // 2. 今日推荐次数
        QueryWrapper<RecommendationLog> todayRecWrapper = new QueryWrapper<>();
        todayRecWrapper.ge("create_time", todayStart);
        stats.setTodayRecommendations(recommendationLogMapper.selectCount(todayRecWrapper));

        // 3. 推荐点击率 (user_clicked=1 / total)
        calculateClickRate(stats);

        // 4. 用户满意度 (user_feedback=1 / total)
        calculateSatisfactionRate(stats);

        // 5. 意图分布
        calculateIntentDistribution(stats);

        // 6. 7日对话趋势
        calculateDailyTrends(stats);

        // 7. 热门商品 TOP5
        calculateHotProducts(stats);

        // 8. 最近推荐记录
        calculateRecentRecommendations(stats);

        return stats;
    }

    /**
     * 计算推荐点击率
     */
    private void calculateClickRate(DashboardStats stats) {
        long totalRecs = recommendationLogMapper.selectCount(new QueryWrapper<>());
        if (totalRecs == 0) {
            stats.setClickRate(0.0);
            return;
        }
        QueryWrapper<RecommendationLog> clickedWrapper = new QueryWrapper<>();
        clickedWrapper.eq("user_clicked", true);
        long clickedCount = recommendationLogMapper.selectCount(clickedWrapper);
        stats.setClickRate((double) clickedCount / totalRecs * 100);
    }

    /**
     * 计算用户满意度
     */
    private void calculateSatisfactionRate(DashboardStats stats) {
        long totalRecs = recommendationLogMapper.selectCount(new QueryWrapper<>());
        if (totalRecs == 0) {
            stats.setSatisfactionRate(0.0);
            return;
        }
        QueryWrapper<RecommendationLog> satisfiedWrapper = new QueryWrapper<>();
        satisfiedWrapper.eq("user_feedback", 1);
        long satisfiedCount = recommendationLogMapper.selectCount(satisfiedWrapper);
        stats.setSatisfactionRate((double) satisfiedCount / totalRecs * 100);
    }

    /**
     * 计算意图分布
     */
    private void calculateIntentDistribution(DashboardStats stats) {
        QueryWrapper<AgentRun> intentWrapper = new QueryWrapper<>();
        intentWrapper.select("intent", "count(*) as count")
                .isNotNull("intent")
                .groupBy("intent");
        List<Map<String, Object>> intentList = agentRunMapper.selectMaps(intentWrapper);

        Map<String, Long> intentMap = new HashMap<>();
        for (Map<String, Object> item : intentList) {
            String intent = (String) item.get("intent");
            Long count = ((Number) item.get("count")).longValue();
            intentMap.put(intent, count);
        }
        stats.setIntentDistribution(intentMap);
    }

    /**
     * 计算7日对话趋势
     */
    private void calculateDailyTrends(DashboardStats stats) {
        QueryWrapper<Conversation> trendWrapper = new QueryWrapper<>();
        trendWrapper.select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                .ge("create_time", LocalDateTime.now().minusDays(7))
                .groupBy("date")
                .orderByAsc("date");
        stats.setDailyTrends(conversationMapper.selectMaps(trendWrapper));
    }

    /**
     * 计算热门商品 TOP5
     */
    private void calculateHotProducts(DashboardStats stats) {
        QueryWrapper<Product> hotWrapper = new QueryWrapper<>();
        hotWrapper.select("title", "brand", "base_price", "image_url", "sales_count", "rating")
                .orderByDesc("sales_count")
                .last("limit 5");
        List<Map<String, Object>> hotList = productMapper.selectMaps(hotWrapper);
        stats.setHotProducts(hotList);
    }

    /**
     * 计算最近推荐记录
     */
    private void calculateRecentRecommendations(DashboardStats stats) {
        QueryWrapper<RecommendationLog> recentWrapper = new QueryWrapper<>();
        recentWrapper.orderByDesc("create_time").last("limit 5");
        List<RecommendationLog> recentList = recommendationLogMapper.selectList(recentWrapper);

        List<Map<String, Object>> recentMaps = new ArrayList<>();
        for (RecommendationLog log : recentList) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", log.getId());
            m.put("query", log.getQuery());
            m.put("intent", log.getIntent());
            m.put("recommendedProductIds", log.getRecommendedProductIds());
            m.put("userClicked", log.getUserClicked());
            m.put("userFeedback", log.getUserFeedback());
            m.put("createTime", log.getCreateTime());
            recentMaps.add(m);
        }
        stats.setRecentRecommendations(recentMaps);
    }
}
