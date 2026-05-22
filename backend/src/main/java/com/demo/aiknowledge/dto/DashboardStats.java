package com.demo.aiknowledge.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DashboardStats {
    // ===== 原有字段（保留兼容） =====
    private Long userCount;
    private Long docCount;
    private Long qaCount;
    private Double hitRate;
    private List<Map<String, Object>> topQuestions;
    private List<Map<String, Object>> unansweredQuestions;
    private List<Map<String, Object>> questionTrends;

    // ===== 新增：电商导购指标 =====

    /** 今日对话数 */
    private Long todayConversations;

    /** 今日推荐次数 */
    private Long todayRecommendations;

    /** 推荐点击率 (user_clicked=1 / total) */
    private Double clickRate;

    /** 用户满意度 (user_feedback=1 / total) */
    private Double satisfactionRate;

    /** 意图分布 (shopping/chitchat/knowledge_qa/unknown 占比) */
    private Map<String, Long> intentDistribution;

    /** 7日对话趋势 (每日对话量) */
    private List<Map<String, Object>> dailyTrends;

    /** 热门商品 TOP5 (按sales_count排序) */
    private List<Map<String, Object>> hotProducts;

    /** 最近推荐记录 (最新5条) */
    private List<Map<String, Object>> recentRecommendations;
}
