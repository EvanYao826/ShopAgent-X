package com.demo.aiknowledge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Conversation;
import com.demo.aiknowledge.entity.Message;
import com.demo.aiknowledge.mapper.ConversationMapper;
import com.demo.aiknowledge.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端 - 对话管理控制器
 * 提供对话列表（分页+筛选）和消息列表查看
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminConversationController {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    /**
     * 获取对话列表，支持分页和userId筛选
     */
    @GetMapping("/conversations")
    public Result<IPage<Conversation>> listConversations(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Conversation::getUserId, userId);
        }
        wrapper.orderByDesc(Conversation::getCreateTime);
        Page<Conversation> pageParam = new Page<>(page, size);
        return Result.success(conversationMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 获取指定对话的消息列表
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<Message>> listMessages(@PathVariable Long id) {
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, id)
                        .orderByAsc(Message::getCreateTime));
        return Result.success(messages);
    }
}
