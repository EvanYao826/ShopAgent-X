package com.demo.aiknowledge.service.impl;

import com.demo.aiknowledge.dto.AgentRunRequest;
import com.demo.aiknowledge.dto.AgentRunResponse;
import com.demo.aiknowledge.entity.AgentRun;
import com.demo.aiknowledge.service.AgentService;
import com.demo.aiknowledge.service.AgentRunService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    @Resource
    private AgentRunService agentRunService;

    @Resource
    private RestTemplate restTemplate;

    @Value("${ai.service.url:http://127.0.0.1:8000/api}")
    private String pythonServiceUrl;

    @Override
    public AgentRunResponse runAgent(AgentRunRequest request) {
        // 生成 runId（如果没有提供）
        String runId = request.getRunId() != null ? request.getRunId() : UUID.randomUUID().toString();

        // 创建 AgentRun 记录
        AgentRun agentRun = new AgentRun();
        agentRun.setId(UUID.randomUUID().toString());
        agentRun.setRunId(runId);
        agentRun.setConversationId(request.getConversationId());
        agentRun.setUserId(request.getUserId());
        agentRun.setStatus("running");
        agentRun.setStartTime(LocalDateTime.now());
        agentRun.setInput(request.getInput());
        agentRun.setCreatedAt(LocalDateTime.now());

        // 保存到数据库
        agentRunService.saveAgentRun(agentRun);

        try {
            // 调用 Python 服务的 Agent 执行接口
            Map<String, Object> pythonRequest = new HashMap<>();
            pythonRequest.put("input", request.getInput());
            pythonRequest.put("conversation_id", request.getConversationId());
            pythonRequest.put("user_id", request.getUserId());
            pythonRequest.put("context", request.getContext() != null ? request.getContext() : "");
            pythonRequest.put("goal", request.getGoal());
            pythonRequest.put("run_id", runId);
            pythonRequest.put("is_admin", request.getIsAdmin() != null ? request.getIsAdmin() : false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(pythonRequest, headers);

            log.info("[AgentService] Calling Python API: {}/api/agent/run, input: {}", pythonServiceUrl, request.getInput().substring(0, Math.min(50, request.getInput().length())));

            // 调用 Python API
            Map<String, Object> pythonResponse = restTemplate.postForObject(
                    pythonServiceUrl + "/api/agent/run",
                    entity,
                    Map.class
            );

            // 更新 AgentRun 状态为 completed
            agentRun.setStatus("completed");
            agentRun.setEndTime(LocalDateTime.now());
            agentRun.setOutput(pythonResponse != null ? (String) pythonResponse.get("answer") : "");
            agentRunService.updateAgentRun(agentRun);

            // 构建响应
            AgentRunResponse response = new AgentRunResponse();
            response.setId(agentRun.getId());
            response.setRunId(agentRun.getRunId());
            response.setTraceId(pythonResponse != null ? (String) pythonResponse.get("trace_id") : runId);
            response.setConversationId(agentRun.getConversationId());
            response.setUserId(agentRun.getUserId());
            response.setStatus("completed");
            response.setStartTime(agentRun.getStartTime());
            response.setEndTime(agentRun.getEndTime());
            response.setInput(agentRun.getInput());
            response.setOutput(agentRun.getOutput());
            response.setCreatedAt(agentRun.getCreatedAt());
            response.setTaskType(pythonResponse != null ? (String) pythonResponse.get("task_type") : "unknown");
            response.setAnswer(pythonResponse != null ? (String) pythonResponse.get("answer") : "");

            log.info("[AgentService] Agent run completed: runId={}, taskType={}", runId, response.getTaskType());

            return response;

        } catch (Exception e) {
            log.error("[AgentService] Failed to call Python API: {}", e.getMessage(), e);

            // 更新 AgentRun 状态为 failed
            agentRun.setStatus("failed");
            agentRun.setEndTime(LocalDateTime.now());
            agentRun.setErrorMessage(e.getMessage());
            agentRunService.updateAgentRun(agentRun);

            // 返回错误响应
            AgentRunResponse response = new AgentRunResponse();
            response.setId(agentRun.getId());
            response.setRunId(agentRun.getRunId());
            response.setConversationId(agentRun.getConversationId());
            response.setUserId(agentRun.getUserId());
            response.setStatus("failed");
            response.setStartTime(agentRun.getStartTime());
            response.setEndTime(agentRun.getEndTime());
            response.setInput(agentRun.getInput());
            response.setErrorMessage(e.getMessage());
            response.setCreatedAt(agentRun.getCreatedAt());

            return response;
        }
    }

    @Override
    public boolean cancelAgentRun(String runId) {
        // 实现取消逻辑
        return agentRunService.updateAgentRunStatus(runId, "cancelled");
    }

    @Override
    public AgentRunResponse retryAgentRun(String runId) {
        // 获取原运行记录
        AgentRun originalRun = agentRunService.getAgentRunByRunId(runId);
        if (originalRun == null) {
            return null;
        }

        // 创建新的运行记录
        AgentRun newRun = new AgentRun();
        newRun.setId(UUID.randomUUID().toString());
        newRun.setRunId(UUID.randomUUID().toString());
        newRun.setConversationId(originalRun.getConversationId());
        newRun.setUserId(originalRun.getUserId());
        newRun.setStatus("running");
        newRun.setStartTime(LocalDateTime.now());
        newRun.setInput(originalRun.getInput());
        newRun.setCreatedAt(LocalDateTime.now());

        // 保存到数据库
        agentRunService.saveAgentRun(newRun);

        // 调用 Python 服务
        AgentRunRequest retryRequest = new AgentRunRequest();
        retryRequest.setInput(originalRun.getInput());
        retryRequest.setConversationId(originalRun.getConversationId());
        retryRequest.setUserId(originalRun.getUserId());
        retryRequest.setRunId(newRun.getRunId());

        return runAgent(retryRequest);
    }
}
