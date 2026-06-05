package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.aiknowledge.config.CacheConfig;
import com.demo.aiknowledge.dto.AiResponse;
import com.demo.aiknowledge.entity.Conversation;
import com.demo.aiknowledge.entity.Message;
import com.demo.aiknowledge.entity.QaLog;
import com.demo.aiknowledge.mapper.ConversationMapper;
import com.demo.aiknowledge.mapper.MessageMapper;
import com.demo.aiknowledge.entity.QaUnanswered;
import com.demo.aiknowledge.mapper.QaLogMapper;
import com.demo.aiknowledge.mapper.QaUnansweredMapper;
import com.demo.aiknowledge.service.AiService;
import com.demo.aiknowledge.service.CacheService;
import com.demo.aiknowledge.service.ChatService;
import com.demo.aiknowledge.service.ConversationContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final QaLogMapper qaLogMapper;
    private final AiService aiService;
    private final QaUnansweredMapper qaUnansweredMapper;
    private final ConversationContextService conversationContextService;
    private final ObjectMapper objectMapper;
    private final CacheService cacheService;
    private final WebClient.Builder webClientBuilder;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    // 线程池用于异步处理 SSE 流
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @Override
    public Conversation createConversation(Long userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title != null ? title : "New Chat " + LocalDateTime.now());
        conversation.setCreateTime(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Override
    public List<Conversation> getHistory(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getIsPinned) // 先按置顶排序
                .orderByDesc(Conversation::getCreateTime)); // 再按时间排序
    }

    @Override
    public Conversation updateConversation(Long conversationId, String title, Boolean isPinned) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            if (title != null) {
                conversation.setTitle(title);
            }
            if (isPinned != null) {
                conversation.setIsPinned(isPinned);
            }
            conversationMapper.updateById(conversation);
        }
        return conversation;
    }

    @Override
    @Transactional
    public Message sendMessage(Long userId, Long conversationId, String content, String jwtToken) {
        // 0. 去重：30秒内相同内容的用户消息不重复插入（防止前端重试导致重复）
        Message existingUserMsg = messageMapper.selectOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .eq(Message::getRole, "user")
                        .eq(Message::getContent, content)
                        .ge(Message::getCreateTime, LocalDateTime.now().minusSeconds(30))
                        .last("LIMIT 1"));
        Message userMsg;
        if (existingUserMsg != null) {
            log.debug("Duplicate user message detected, skipping insert. conversationId={}", conversationId);
            userMsg = existingUserMsg;
        } else {
            // 1. 保存用户消息
            userMsg = new Message();
            userMsg.setConversationId(conversationId);
            userMsg.setRole("user");
            userMsg.setContent(content);
            userMsg.setCreateTime(LocalDateTime.now());
            messageMapper.insert(userMsg);
        }

        // 1.1 更新对话上下文（用户消息）
        conversationContextService.updateConversationContext(conversationId, userId, userMsg);

        // 检查是否为第一条消息，如果是则生成标题
        Long msgCount = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId));
        if (msgCount == 1) { // 明确判断是否为第一条消息
             // 异步生成标题，避免阻塞
             aiService.generateTitle(conversationId, content);
        }

        // 2. 获取对话上下文（获取最近10条消息，包含刚插入的用户消息）
        List<Message> contextMessages = conversationContextService.getConversationContext(conversationId, 10);
        // 构建上下文字符串
        StringBuilder contextBuilder = new StringBuilder();
        for (Message msg : contextMessages) {
            contextBuilder.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        String conversationContext = contextBuilder.toString();
        log.debug("对话上下文构建完成，长度: {}，内容: {}", conversationContext.length(), conversationContext);

        // 3. 调用 AI 服务获取回答（传入对话上下文）
        AiResponse aiResponse = aiService.ask(content, conversationContext, userId);
        String answer = aiResponse.getAnswer();
        String sourcesJson = null;
        String taskType = aiResponse.getTaskType();

        if (aiResponse.getSources() != null && !aiResponse.getSources().isEmpty()) {
            try {
                sourcesJson = objectMapper.writeValueAsString(aiResponse.getSources());
            } catch (Exception e) {
                log.error("Failed to serialize sources", e);
            }
        } else {
            // 如果没有 sources 或者 answer 看起来像不知道，记录到 unanswered
            // 简单的判断逻辑：如果 answer 包含 "不知道" 或 sources 为空且 answer 很短?
            // 这里假设 sources 为空且 answer 是兜底回复时记录
            if (answer.contains("抱歉") || answer.contains("无法回答")) {
                QaUnanswered existing = qaUnansweredMapper.selectOne(
                        new LambdaQueryWrapper<QaUnanswered>().eq(QaUnanswered::getQuestion, content));
                if (existing != null) {
                    existing.setCount(existing.getCount() + 1);
                    existing.setLastUserId(userId);
                    existing.setUpdateTime(LocalDateTime.now());
                    qaUnansweredMapper.updateById(existing);
                } else {
                    QaUnanswered ua = new QaUnanswered();
                    ua.setQuestion(content);
                    ua.setCount(1);
                    ua.setLastUserId(userId);
                    ua.setCreateTime(LocalDateTime.now());
                    qaUnansweredMapper.insert(ua);
                }
            }
        }

        // 3. 保存 AI 回答
        Message aiMsg = new Message();
        aiMsg.setConversationId(conversationId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(answer);
        aiMsg.setSources(sourcesJson);
        aiMsg.setTaskType(taskType);
        // 如果有商品卡片，直接设置（JacksonTypeHandler 自动序列化）
        if (aiResponse.getProductCards() != null && !aiResponse.getProductCards().isEmpty()) {
            aiMsg.setProductCards(aiResponse.getProductCards());
            aiMsg.setMessageType("product_card");
        }
        // 如果有确认卡片（购物车删除/修改确认）
        if (aiResponse.getConfirmCard() != null) {
            aiMsg.setConfirmCard(aiResponse.getConfirmCard());
            aiMsg.setMessageType("confirm_card");
        }
        aiMsg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(aiMsg);

        // 3.1 更新对话上下文（AI消息）
        conversationContextService.updateConversationContext(conversationId, userId, aiMsg);

        // 4. 记录 QA 日志
        QaLog qaLog = new QaLog();
        qaLog.setUserId(userId);
        qaLog.setConversationId(conversationId);
        qaLog.setQuestion(content);
        qaLog.setAnswer(answer);
        qaLog.setTaskType(taskType);
        qaLog.setCreateTime(LocalDateTime.now());
        qaLogMapper.insert(qaLog);

        return aiMsg; // 返回 AI 的回答
    }

    @Override
    public List<Message> getMessages(Long conversationId) {
        // 使用对话上下文服务获取消息，支持滑动窗口和缓存
        return conversationContextService.getConversationContext(conversationId, 20);
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        // 删除会话相关的消息
        messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, conversationId));
        // 删除会话本身
        conversationMapper.deleteById(conversationId);
    }

    @Override
    @Transactional
    public Message submitFeedback(Long messageId, String feedbackType) {
        // 1. 查找消息
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new RuntimeException("消息不存在");
        }

        // 2. 更新反馈字段
        message.setFeedbackType(feedbackType);
        message.setFeedbackTime(LocalDateTime.now());
        messageMapper.updateById(message);

        // 3. 清除该会话的缓存，确保下次获取时从数据库读取最新数据
        String cacheKey = CacheConfig.CacheConstants.KEY_CONVERSATION_CONTEXT + message.getConversationId();
        cacheService.delete(CacheConfig.CacheConstants.CACHE_CONVERSATION_CONTEXT, cacheKey);
        log.debug("Cleared conversation context cache for conversationId: {}", message.getConversationId());

        // 4. 如果是AI消息，同步更新QA日志的反馈
        if ("assistant".equals(message.getRole())) {
            QaLog qaLog = qaLogMapper.selectOne(new LambdaQueryWrapper<QaLog>()
                    .eq(QaLog::getAnswer, message.getContent())
                    .orderByDesc(QaLog::getCreateTime)
                    .last("LIMIT 1"));
            if (qaLog != null) {
                qaLog.setFeedbackType(feedbackType);
                qaLog.setFeedbackTime(LocalDateTime.now());
                qaLogMapper.updateById(qaLog);
            }
        }

        return message;
    }

    @Override
    @Transactional
    public Message saveMessage(Long conversationId, String role, String content, String messageType) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setMessageType(messageType != null ? messageType : "text");
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);

        // 清除该会话的缓存
        String cacheKey = CacheConfig.CacheConstants.KEY_CONVERSATION_CONTEXT + conversationId;
        cacheService.delete(CacheConfig.CacheConstants.CACHE_CONVERSATION_CONTEXT, cacheKey);

        return msg;
    }

    @Override
    public SseEmitter sendStreamMessage(Long userId, Long conversationId, String content, String username, boolean isAdmin, String jwtToken) {
        return sendStreamMessage(userId, conversationId, content, username, isAdmin, null, null, null, jwtToken);
    }

    @Override
    public SseEmitter sendStreamMessage(Long userId, Long conversationId, String content,
                                         String username, boolean isAdmin,
                                         String gender, String skinType, List<String> preferenceTags,
                                         String jwtToken) {
        // 设置超时时间（5分钟）
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        sseExecutor.execute(() -> {
            try {
                // 0. 去重：30秒内相同内容的用户消息不重复插入（防止前端重试导致重复）
                Message existingUserMsg = messageMapper.selectOne(
                        new LambdaQueryWrapper<Message>()
                                .eq(Message::getConversationId, conversationId)
                                .eq(Message::getRole, "user")
                                .eq(Message::getContent, content)
                                .ge(Message::getCreateTime, LocalDateTime.now().minusSeconds(30))
                                .last("LIMIT 1"));
                Message userMsg;
                if (existingUserMsg != null) {
                    log.debug("Duplicate user message detected in stream, skipping insert. conversationId={}", conversationId);
                    userMsg = existingUserMsg;
                } else {
                    // 1. 保存用户消息
                    userMsg = new Message();
                    userMsg.setConversationId(conversationId);
                    userMsg.setRole("user");
                    userMsg.setContent(content);
                    userMsg.setCreateTime(LocalDateTime.now());
                    messageMapper.insert(userMsg);
                }

                // 1.1 更新对话上下文（用户消息）
                conversationContextService.updateConversationContext(conversationId, userId, userMsg);

                // 检查是否为第一条消息，如果是则生成标题
                Long msgCount = messageMapper.selectCount(
                        new LambdaQueryWrapper<Message>()
                                .eq(Message::getConversationId, conversationId));
                if (msgCount == 1) {
                    aiService.generateTitle(conversationId, content);
                }

                // 2. 获取对话上下文
                List<Message> contextMessages = conversationContextService.getConversationContext(conversationId, 10);
                StringBuilder contextBuilder = new StringBuilder();
                for (Message msg : contextMessages) {
                    contextBuilder.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                }
                String conversationContext = contextBuilder.toString();

                // 3. 构建请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("question", content);
                requestBody.put("conversation_id", conversationId.toString());
                requestBody.put("context", conversationContext);
                if (username != null) {
                    requestBody.put("username", username);
                }
                requestBody.put("is_admin", isAdmin);
                requestBody.put("user_id", userId.toString());
                // 用户画像
                if (gender != null) {
                    requestBody.put("gender", gender);
                }
                if (skinType != null) {
                    requestBody.put("skin_type", skinType);
                }
                if (preferenceTags != null && !preferenceTags.isEmpty()) {
                    requestBody.put("preference_tags", preferenceTags);
                }
                // JWT token（供 Python 调用 Java API 时使用）
                if (jwtToken != null) {
                    requestBody.put("jwt_token", jwtToken);
                }

                // 4. 调用 Python SSE 流式接口并透传事件
                WebClient webClient = webClientBuilder.baseUrl(aiServiceUrl).build();

                StringBuilder fullAnswer = new StringBuilder();
                String[] taskTypeHolder = {null};
                Object[] productCardsHolder = {null};
                Object[] confirmCardHolder = {null};
                Object[] cartSelectionHolder = {null};
                String[] cartSelectionTypeHolder = {null};

                Flux<String> eventStream = webClient.post()
                        .uri("/ask/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToFlux(String.class)
                        .timeout(Duration.ofMinutes(4));

                // 订阅并处理每个 SSE 事件
                eventStream.subscribe(
                        eventLine -> {
                            try {
                                // Python 返回格式: "data: {...}\n\n"
                                String jsonStr = eventLine.trim();
                                if (jsonStr.startsWith("data: ")) {
                                    jsonStr = jsonStr.substring(6);
                                }
                                if (jsonStr.isEmpty()) return;

                                // 解析事件
                                @SuppressWarnings("unchecked")
                                Map<String, Object> eventMap = objectMapper.readValue(jsonStr, Map.class);
                                String type = (String) eventMap.get("type");

                                // 收集完整回答
                                if ("token".equals(type) || "answer".equals(type)) {
                                    String tokenContent = (String) eventMap.get("content");
                                    if (tokenContent != null) {
                                        fullAnswer.append(tokenContent);
                                    }
                                }
                                if ("routed".equals(type)) {
                                    taskTypeHolder[0] = (String) eventMap.get("task_type");
                                }
                                if ("product_cards".equals(type)) {
                                    productCardsHolder[0] = eventMap.get("product_cards");
                                    log.info("SSE product_cards received: {}", eventMap.get("product_cards"));
                                }
                                // 确认卡片事件：保存确认卡片数据
                                if ("confirm_card".equals(type)) {
                                    Object nestedCard = eventMap.get("confirm_card");
                                    if (nestedCard instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> cardMap = (Map<String, Object>) nestedCard;
                                        confirmCardHolder[0] = cardMap;
                                        String confirmMsg = (String) cardMap.get("message");
                                        if (confirmMsg != null && !confirmMsg.isEmpty()) {
                                            fullAnswer.append(confirmMsg);
                                        }
                                    }
                                    taskTypeHolder[0] = "cart";
                                }

                                // 购物车选择/列表事件：保存数据用于持久化
                                if ("cart_selection".equals(type) || "cart_list".equals(type)) {
                                    Object nested = eventMap.get("cart_selection");
                                    if (nested == null) nested = eventMap.get("cart_list");
                                    if (nested instanceof Map) {
                                        cartSelectionHolder[0] = nested;
                                        cartSelectionTypeHolder[0] = type;
                                        // 用 message 字段作为 fullAnswer
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> csMap = (Map<String, Object>) nested;
                                        String csMsg = (String) csMap.get("message");
                                        if (csMsg != null && !csMsg.isEmpty()) {
                                            fullAnswer.append(csMsg);
                                        }
                                    }
                                    taskTypeHolder[0] = "cart";
                                }

                                // 透传事件给客户端
                                emitter.send(SseEmitter.event()
                                        .name(type)
                                        .data(eventMap));

                                log.debug("SSE event forwarded: type={}, content={}", type, eventMap.get("content"));
                            } catch (Exception e) {
                                log.error("Error processing SSE event: {}", eventLine, e);
                            }
                        },
                        error -> {
                            log.error("SSE stream error", error);
                            try {
                                Map<String, Object> errorEvent = new HashMap<>();
                                errorEvent.put("type", "error");
                                errorEvent.put("content", "AI服务暂时不可用，请稍后再试");
                                emitter.send(SseEmitter.event().name("error").data(errorEvent));
                            } catch (Exception e) {
                                log.error("Failed to send error event", e);
                            }
                            emitter.complete();
                        },
                        () -> {
                            // 流完成 - 保存 AI 回答到数据库（跳过空回答，避免重试产生空消息）
                            try {
                                String answer = fullAnswer.toString();
                                String taskType = taskTypeHolder[0];

                                if ((answer == null || answer.trim().isEmpty()) && confirmCardHolder[0] == null && cartSelectionHolder[0] == null) {
                                    log.info("SSE stream completed but answer is empty, skipping save. conversationId={}", conversationId);
                                    emitter.complete();
                                    return;
                                }

                                // 如果 fullAnswer 为空但有确认卡片，用确认消息作为内容
                                if ((answer == null || answer.trim().isEmpty()) && confirmCardHolder[0] != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> card = (Map<String, Object>) confirmCardHolder[0];
                                    answer = (String) card.getOrDefault("message", "请确认操作");
                                }

                                // 如果 fullAnswer 为空但有购物车卡片，用其 message 作为内容
                                if ((answer == null || answer.trim().isEmpty()) && cartSelectionHolder[0] != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> cs = (Map<String, Object>) cartSelectionHolder[0];
                                    answer = (String) cs.getOrDefault("message", "购物车");
                                }

                                Message aiMsg = new Message();
                                aiMsg.setConversationId(conversationId);
                                aiMsg.setRole("assistant");
                                aiMsg.setContent(answer);
                                aiMsg.setTaskType(taskType);

                                // 设置商品卡片
                                if (productCardsHolder[0] != null) {
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> cards = (List<Map<String, Object>>) productCardsHolder[0];
                                    aiMsg.setProductCards(cards);
                                    aiMsg.setMessageType("product_card");
                                }

                                // 设置确认卡片
                                if (confirmCardHolder[0] != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> card = (Map<String, Object>) confirmCardHolder[0];
                                    aiMsg.setConfirmCard(card);
                                    aiMsg.setMessageType("confirm_card");
                                }

                                // 设置购物车选择/列表卡片
                                if (cartSelectionHolder[0] != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> cs = (Map<String, Object>) cartSelectionHolder[0];
                                    aiMsg.setCartSelection(cs);
                                    aiMsg.setMessageType(cartSelectionTypeHolder[0] != null ? cartSelectionTypeHolder[0] : "cart_selection");
                                }

                                aiMsg.setCreateTime(LocalDateTime.now());
                                messageMapper.insert(aiMsg);

                                // 更新对话上下文（AI消息）
                                conversationContextService.updateConversationContext(conversationId, userId, aiMsg);

                                // 记录 QA 日志
                                QaLog qaLog = new QaLog();
                                qaLog.setUserId(userId);
                                qaLog.setConversationId(conversationId);
                                qaLog.setQuestion(content);
                                qaLog.setAnswer(answer);
                                qaLog.setTaskType(taskType);
                                qaLog.setCreateTime(LocalDateTime.now());
                                qaLogMapper.insert(qaLog);

                                log.info("SSE stream completed, answer saved. conversationId={}, length={}", conversationId, answer.length());
                            } catch (Exception e) {
                                log.error("Failed to save AI response after SSE stream", e);
                            }
                            emitter.complete();
                        }
                );

            } catch (Exception e) {
                log.error("SSE stream initialization failed", e);
                try {
                    Map<String, Object> errorEvent = new HashMap<>();
                    errorEvent.put("type", "error");
                    errorEvent.put("content", "系统错误，请稍后再试");
                    emitter.send(SseEmitter.event().name("error").data(errorEvent));
                } catch (Exception ex) {
                    log.error("Failed to send error event", ex);
                }
                emitter.complete();
            }
        });

        // 注册超时和完成回调
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout for conversationId={}", conversationId);
            emitter.complete();
        });
        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed for conversationId={}", conversationId);
        });

        return emitter;
    }
}