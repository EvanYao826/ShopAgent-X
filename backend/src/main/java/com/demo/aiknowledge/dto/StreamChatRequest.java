package com.demo.aiknowledge.dto;

import lombok.Data;

/**
 * SSE 流式聊天请求 DTO
 */
@Data
public class StreamChatRequest {
    private Long userId;
    private Long conversationId;
    private String content;
    private String username;
    private boolean isAdmin;
}
