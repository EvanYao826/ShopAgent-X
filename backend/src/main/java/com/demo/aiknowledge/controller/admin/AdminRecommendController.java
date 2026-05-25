package com.demo.aiknowledge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.RecommendationLog;
import com.demo.aiknowledge.mapper.RecommendationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端 - 推荐统计控制器
 * 提供推荐效果统计和日志查询
 */
@RestController
@RequestMapping("/api/admin/recommend")
@RequiredArgsConstructor
public class AdminRecommendController {

    private final RecommendationLogMapper recommendationLogMapper;

    /** 推荐效果统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> result = new HashMap<>();
        long total = recommendationLogMapper.selectCount(null);

        long clicked = recommendationLogMapper.selectCount(
                new LambdaQueryWrapper<RecommendationLog>()
                        .eq(RecommendationLog::getUserClicked, true));

        long satisfied = recommendationLogMapper.selectCount(
                new LambdaQueryWrapper<RecommendationLog>()
                        .eq(RecommendationLog::getUserFeedback, 2));

        long dissatisfied = recommendationLogMapper.selectCount(
                new LambdaQueryWrapper<RecommendationLog>()
                        .eq(RecommendationLog::getUserFeedback, 1));

        long noFeedback = total - satisfied - dissatisfied;

        result.put("totalRecommendations", total);
        result.put("clickRate", total > 0 ? Math.round(clicked * 100.0 / total) : 0);
        result.put("satisfactionRate", total > 0 ? Math.round(satisfied * 100.0 / total) : 0);
        result.put("noFeedbackRate", total > 0 ? Math.round(noFeedback * 100.0 / total) : 0);
        result.put("clickedCount", clicked);
        result.put("satisfiedCount", satisfied);
        result.put("noFeedbackCount", noFeedback);

        return Result.success(result);
    }

    /** 推荐日志列表（分页） */
    @GetMapping("/logs")
    public Result<IPage<RecommendationLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<RecommendationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(RecommendationLog::getCreateTime);
        return Result.success(recommendationLogMapper.selectPage(new Page<>(page, size), wrapper));
    }
}
