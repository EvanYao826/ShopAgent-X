package com.demo.aiknowledge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Conversation;
import com.demo.aiknowledge.entity.Message;
import com.demo.aiknowledge.mapper.ConversationMapper;
import com.demo.aiknowledge.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin 对话管理控制器 —— 查看用户对话记录、消息详情、统计数据
 */
@RestController
@RequestMapping("/api/admin/conversation")
@RequiredArgsConstructor
public class AdminConversationController {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @GetMapping("/list")
    public Result<IPage<Conversation>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword) {
        QueryWrapper<Conversation> qw = new QueryWrapper<>();
        if (userId != null) qw.eq("user_id", userId);
        if (keyword != null && !keyword.isEmpty()) qw.like("title", keyword);
        qw.orderByDesc("update_time");
        IPage<Conversation> result = conversationMapper.selectPage(new Page<>(page, size), qw);

        // 批量查询每个对话的消息数
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            List<Long> convIds = result.getRecords().stream()
                    .map(Conversation::getId).collect(java.util.stream.Collectors.toList());
            QueryWrapper<Message> mqw = new QueryWrapper<>();
            mqw.select("conversation_id", "count(*) as cnt")
                    .in("conversation_id", convIds)
                    .in("role", "user", "assistant")
                    .groupBy("conversation_id");
            java.util.Map<Long, Long> countMap = new java.util.HashMap<>();
            messageMapper.selectMaps(mqw).forEach(m -> {
                Object convId = m.get("conversation_id");
                Object cnt = m.get("cnt");
                if (convId != null && cnt != null) {
                    countMap.put(Long.valueOf(convId.toString()), ((Number) cnt).longValue());
                }
            });
            result.getRecords().forEach(c ->
                    c.setMessageCount(countMap.getOrDefault(c.getId(), 0L).intValue()));
        }

        return Result.success(result);
    }

    @GetMapping("/{id}/messages")
    public Result<List<Message>> messages(@PathVariable Long id) {
        QueryWrapper<Message> qw = new QueryWrapper<>();
        qw.eq("conversation_id", id).orderByAsc("create_time");
        return Result.success(messageMapper.selectList(qw));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConversations", conversationMapper.selectCount(null));
        stats.put("totalMessages", messageMapper.selectCount(null));
        stats.put("todayConversations",
            conversationMapper.selectCount(new QueryWrapper<Conversation>()
                .ge("create_time", java.time.LocalDate.now().atStartOfDay())));
        return Result.success(stats);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        messageMapper.delete(new QueryWrapper<Message>().eq("conversation_id", id));
        conversationMapper.deleteById(id);
        return Result.success(null);
    }
}
