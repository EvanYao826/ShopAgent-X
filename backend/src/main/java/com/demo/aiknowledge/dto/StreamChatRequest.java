package com.demo.aiknowledge.dto;

import lombok.Data;

import java.util.List;

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
    private String gender;
    private String skinType;
    private List<String> preferenceTags;
}
