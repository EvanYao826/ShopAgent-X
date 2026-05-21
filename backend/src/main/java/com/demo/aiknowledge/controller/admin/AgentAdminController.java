package com.demo.aiknowledge.controller.admin;

import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.AgentRun;
import com.demo.aiknowledge.entity.ToolCall;
import com.demo.aiknowledge.service.AgentRunService;
import com.demo.aiknowledge.service.ToolCallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/agent")
@RequiredArgsConstructor
public class AgentAdminController {

    private final AgentRunService agentRunService;
    private final ToolCallService toolCallService;

    @GetMapping("/runs")
    public Result<List<AgentRun>> getAgentRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId) {
        if (status != null) {
            return Result.success(agentRunService.getAgentRunsByStatus(status));
        }
        if (userId != null) {
            return Result.success(agentRunService.getAgentRunsByUserId(userId));
        }
        return Result.success(agentRunService.getAllAgentRuns(page, size));
    }

    @GetMapping("/runs/{runId}")
    public Result<AgentRun> getAgentRun(@PathVariable String runId) {
        AgentRun run = agentRunService.getAgentRunByRunId(runId);
        if (run == null) {
            return Result.error("运行记录不存在");
        }
        return Result.success(run);
    }

    @GetMapping("/tool-calls")
    public Result<List<ToolCall>> getToolCalls(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(toolCallService.getAllToolCalls(page, size));
    }

    @GetMapping("/tool-calls/failed")
    public Result<List<ToolCall>> getFailedToolCalls(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(toolCallService.getFailedToolCalls(limit));
    }
}
