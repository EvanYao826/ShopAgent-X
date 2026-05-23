package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.Conversation;
import com.demo.aiknowledge.entity.Message;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {
    Conversation createConversation(Long userId, String title);
    List<Conversation> getHistory(Long userId);
    Message sendMessage(Long userId, Long conversationId, String content);
    List<Message> getMessages(Long conversationId);
    void deleteConversation(Long conversationId);
    Conversation updateConversation(Long conversationId, String title, Boolean isPinned);
    Message submitFeedback(Long messageId, String feedbackType);

    /**
     * SSE 流式发送消息，透传 Python AI 服务的 SSE 事件流
     *
     * @param userId         用户ID
     * @param conversationId 对话ID
     * @param content        消息内容
     * @param username       用户名（可选）
     * @param isAdmin        是否管理员
     * @return SSE 发射器
     */
    SseEmitter sendStreamMessage(Long userId, Long conversationId, String content, String username, boolean isAdmin);
}
