package com.demo.aiknowledge.controller.admin;

import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Conversation;
import com.demo.aiknowledge.entity.Message;
import com.demo.aiknowledge.mapper.ConversationMapper;
import com.demo.aiknowledge.mapper.MessageMapper;
import com.demo.aiknowledge.service.AiService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Admin 管理助手 - 完整的对话管理
 */
@RestController
@RequestMapping("/api/admin-chat")
@RequiredArgsConstructor
@Slf4j
public class AdminChatController {

    private final AiService aiService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    // ========== 对话管理 ==========

    /** 创建新对话 */
    @PostMapping("/conversations")
    public Result<Conversation> createConversation(
            @RequestParam Long adminId,
            @RequestParam(defaultValue = "新对话") String title) {
        Conversation conv = new Conversation();
        conv.setUserId(adminId);
        conv.setTitle(title);
        conv.setScene("admin_chat");
        conv.setIsPinned(false);
        conv.setCreateTime(LocalDateTime.now());
        conv.setUpdateTime(LocalDateTime.now());
        conversationMapper.insert(conv);
        log.info("[AdminChat] Created conversation: id={}, adminId={}", conv.getId(), adminId);
        return Result.success(conv);
    }

    /** 获取对话列表 - 只返回管理助手对话 */
    @GetMapping("/conversations")
    public Result<List<Conversation>> getConversations(@RequestParam Long adminId) {
        List<Conversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, adminId)
                        .eq(Conversation::getScene, "admin_chat")
                        .orderByDesc(Conversation::getIsPinned)
                        .orderByDesc(Conversation::getUpdateTime)
        );
        return Result.success(list);
    }

    /** 更新对话（重命名/置顶） */
    @PutMapping("/conversations/{id}")
    public Result<String> updateConversation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) return Result.error("对话不存在");

        if (body.containsKey("title")) {
            conv.setTitle((String) body.get("title"));
        }
        if (body.containsKey("isPinned")) {
            conv.setIsPinned((Boolean) body.get("isPinned"));
        }
        conv.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return Result.success("ok");
    }

    /** 删除对话 */
    @DeleteMapping("/conversations/{id}")
    public Result<String> deleteConversation(@PathVariable Long id) {
        // 删除消息
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, id));
        conversationMapper.deleteById(id);
        return Result.success("ok");
    }

    // ========== 消息管理 ==========

    /** 获取对话消息 */
    @GetMapping("/messages")
    public Result<List<Message>> getMessages(@RequestParam Long conversationId) {
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime)
        );
        return Result.success(messages);
    }

    /** 发送消息并获取AI回复 */
    @PostMapping("/messages")
    public Result<Message> sendMessage(
            @RequestParam Long adminId,
            @RequestParam Long conversationId,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return Result.error("消息内容不能为空");
        }

        // 1. 保存用户消息
        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(content);
        userMsg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(userMsg);

        // 2. 更新对话时间和标题
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setUpdateTime(LocalDateTime.now());
            if (conv.getTitle() == null || conv.getTitle().startsWith("对话 ")) {
                String shortTitle = content.length() > 20 ? content.substring(0, 20) + "..." : content;
                conv.setTitle(shortTitle);
            }
            conversationMapper.updateById(conv);
        }

        // 3. 调用AI
        Map<String, Object> aiResult = aiService.askForAdmin(content, "", adminId);
        String answer = (String) aiResult.getOrDefault("answer", "抱歉，服务暂时不可用。");
        String taskType = (String) aiResult.getOrDefault("task_type", "admin_copilot");

        // 4. 保存AI回复
        Message aiMsg = new Message();
        aiMsg.setConversationId(conversationId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(answer);
        aiMsg.setTaskType(taskType);
        aiMsg.setCreateTime(LocalDateTime.now());

        Object sources = aiResult.get("sources");
        if (sources != null) {
            aiMsg.setSources(sources.toString());
        }
        messageMapper.insert(aiMsg);

        log.info("[AdminChat] Message sent: convId={}, answerLen={}, taskType={}",
                conversationId, answer.length(), taskType);

        return Result.success(aiMsg);
    }

    /** 消息反馈 */
    @PostMapping("/messages/feedback")
    public Result<String> submitFeedback(@RequestBody Map<String, Object> body) {
        Long messageId = body.get("messageId") != null
                ? Long.valueOf(body.get("messageId").toString()) : null;
        String feedbackType = (String) body.get("feedbackType");

        if (messageId == null || feedbackType == null) {
            return Result.error("参数不完整");
        }

        Message msg = messageMapper.selectById(messageId);
        if (msg != null) {
            msg.setFeedbackType(feedbackType);
            msg.setFeedbackTime(LocalDateTime.now());
            messageMapper.updateById(msg);
        }
        return Result.success("ok");
    }
}
