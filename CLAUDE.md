# CLAUDE.md — ShopAgent-X 开发守则

> 每次执行任务前必须先阅读本文件。修改任何代码前先理解上下游依赖。

---

## 一、项目架构速查

三端架构：**Android App** (Kotlin Compose) → **Java Backend** (Spring Boot 3.2, :8080) → **Python AI Service** (FastAPI, :8000)

Python 端核心链路：
```
RouterAgent（意图分类+复杂度判断）
  ├── 简单链路：ChitChatAgent / ShoppingAgent / KnowledgeQAAgent(L1/L2)
  └── 复杂链路（走Orchestrator编排）：KnowledgeQAAgent(L3) / ReasoningAgent
```

关键文件：
- `backend/python-service/workflows/` — 所有 Agent
- `backend/python-service/tools/` — 所有工具 + `registry.py`（注册中心）
- `backend/python-service/agent/` — 五层编排框架（orchestrator/planner/executor/state/events/policies）
- `backend/python-service/intent/classifier.py` — 意图分类器
- `backend/python-service/core/` — LLM/向量库/MySQL/Redis 客户端
- `backend/src/main/java/com/demo/aiknowledge/` — Java 后端

---

## 二、绝对禁止（红线）

1. **禁止绕过 ToolRegistry 直接实例化工具** — 必须用 `tool_registry.invoke_tool("tool_name", params, run_id)`
2. **禁止信任客户端传入的 userId** — Java 端必须从 JWT 获取：`SecurityContextHolder.getContext().getAuthentication().getName()`
3. **禁止硬编码 API Key / 密码** — 使用环境变量或 `.env` 文件
4. **禁止只实现流式或非流式中的一个** — 每个 Agent 必须同时有 `handle()` 和 `handle_stream()` 两个版本
5. **禁止删除文件** — 如有无用文件，移到 `D:\AI项目资料` 或用 `.gitignore` 排除

---

## 三、新增代码规范

### 新增 Tool
1. 继承 `Tool` 基类（`tools/base.py`）
2. 实现 `execute(parameters: Dict) -> Dict`
3. 在 `tools/__init__.py` 的 `register_all_tools()` 中注册
4. 添加中文注释说明用途和参数

### 新增 Agent
1. 继承 `BaseAgent`（`workflows/base_agent.py`）
2. 实现同步 + 流式两个方法
3. 在 `RouterAgent` 中添加路由分支
4. 在 `IntentClassifier` 中添加意图关键词
5. 返回格式必须统一：
```python
{"answer": str, "sources": list, "has_sources": bool, "task_type": str, "product_cards": list}
```

### SSE 流式事件格式
```json
{"type": "routed", "task_type": "shopping"}
{"type": "token", "content": "推荐"}
{"type": "product_cards", "product_cards": [...], "sources": [...]}
{"type": "end"}
{"type": "error", "content": "错误信息"}
```

---

## 四、已知坑点（修改相关代码时注意）

| 问题 | 位置 | 说明 |
|------|------|------|
| 线程池重复创建 | `tools/registry.py` | `invoke_tool()` 每次 `ThreadPoolExecutor(max_workers=1)`，应改为共享 |
| 内存泄漏 | `tools/execution.py` | `ToolExecutionTracker` 只增不减，需加容量上限 |
| 记忆写入重复 | `base_agent.py` + `memory_agent.py` | 两处都在写，应统一 |
| 流式不读记忆 | `chitchat_agent.py` | `chat_stream()` 没读会话记忆 |
| 工具直接实例化 | `retrieval_agent.py` 等 | 部分 Agent 绕过了 ToolRegistry |
| IDOR 漏洞 | `ChatController.sendMessage` | userId 应从 JWT 获取 |

---

## 五、每次改完代码必须自检

- [ ] 我是否理解了这个改动涉及的所有上下游模块？
- [ ] 是否破坏了现有的核心流程（对话→流式回复→商品卡片）？
- [ ] Python 新工具/Agent 是否通过 ToolRegistry 注册？
- [ ] 流式和非流式两个版本是否都实现了？
- [ ] 异常处理是否完善？（try-except + fallback 兜底回复）
- [ ] 是否添加了中文注释？
- [ ] Java 端的 userId 是否从 JWT 获取？

---

## 六、启动顺序

```
1. docker-compose up -d                              # MySQL + Redis
2. cd backend/python-service && python main.py       # Python (port 8000)
3. cd backend && mvn spring-boot:run                 # Java (port 8080)
4. cd backend/frontend && npm run dev                # 前端 (port 5173, 可选)
```
