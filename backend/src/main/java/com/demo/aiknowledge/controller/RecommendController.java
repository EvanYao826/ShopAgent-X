package com.demo.aiknowledge.controller;

import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.RecommendationLog;
import com.demo.aiknowledge.entity.UserFavorite;
import com.demo.aiknowledge.entity.UserBrowseHistory;
import com.demo.aiknowledge.service.RecommendationLogService;
import com.demo.aiknowledge.service.UserFavoriteService;
import com.demo.aiknowledge.service.UserBrowseHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendationLogService recommendationLogService;
    private final UserFavoriteService userFavoriteService;
    private final UserBrowseHistoryService userBrowseHistoryService;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PostMapping("/feedback")
    public Result<Void> feedback(@RequestParam Long id, @RequestParam Integer feedback) {
        recommendationLogService.updateFeedback(id, feedback);
        return Result.success(null);
    }

    @PostMapping("/browse")
    public Result<Void> recordBrowse(@RequestBody UserBrowseHistory history) {
        history.setUserId(getCurrentUserId());
        userBrowseHistoryService.saveOrUpdate(history);
        return Result.success(null);
    }

    @PostMapping("/favorite/add")
    public Result<Void> addFavorite(@RequestParam Long productId) {
        userFavoriteService.addFavorite(getCurrentUserId(), productId);
        return Result.success(null);
    }

    @PostMapping("/favorite/remove")
    public Result<Void> removeFavorite(@RequestParam Long productId) {
        userFavoriteService.removeFavorite(getCurrentUserId(), productId);
        return Result.success(null);
    }

    @GetMapping("/favorite/list")
    public Result<List<UserFavorite>> listFavorites() {
        return Result.success(userFavoriteService.listByUserId(getCurrentUserId()));
    }

    @GetMapping("/browse/history")
    public Result<List<UserBrowseHistory>> browseHistory() {
        return Result.success(userBrowseHistoryService.listByUserId(getCurrentUserId()));
    }

    @DeleteMapping("/browse/history/{id}")
    public Result<String> deleteHistory(@PathVariable Long id) {
        userBrowseHistoryService.deleteHistory(getCurrentUserId(), id);
        return Result.success("已删除");
    }
}
