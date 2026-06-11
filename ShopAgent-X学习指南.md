# ShopAgent-X 学习指南

> 配合 [README.md](./README.md) 使用
> README 告诉你"这个项目是什么"，本指南告诉你"怎么学、怎么用、怎么讲"

---

## 目录

1. [本指南的定位](#1-本指南的定位)
2. [项目核心亮点](#2-项目核心亮点)
3. [前置知识清单](#3-前置知识清单)
4. [和 LangGraph 的区别与迁移能力](#4-和-langgraph-的区别与迁移能力)
5. [分阶段学习路线](#5-分阶段学习路线)
6. [核心代码逐行解读](#6-核心代码逐行解读)
7. [如何验证执行流程](#7-如何验证执行流程)
8. [面试高频问题与回答模板](#8-面试高频问题与回答模板)
9. [踩坑与经验教训](#9-踩坑与经验教训)
10. [学后收获与扩展建议](#10-学后收获与扩展建议)

---

## 1. 本指南的定位

README 已经说明了：
- 项目是什么、技术栈是什么
- 五层架构、多 Agent 协作、Tool Registry、全链路追踪的亮点描述
- 架构图（mermaid）、效果截图、快速启动方法
- 简历写法参考

**本指南聚焦 README 没有的内容：**
- 每个模块的代码怎么读、核心逻辑是什么
- 学习路线：先学什么、后学什么、每步花多久
- 面试怎么答：高频问题 + 回答模板
- 真实踩坑：项目迭代中遇到的问题和解决方案
- 迁移能力：学完后能在任何项目中从零构建 Agent

---

## 2. 项目核心亮点

> **关于本项目的学习方式**：本项目的重点不在于跑通所有功能，而在于**源码阅读和架构理解**。即使项目无法完整运行，只要你能读懂每一层的代码、理解每个设计决策的原因、讲清楚模块之间的协作关系，面试时就能展现出真正的技术深度。跑通项目只是辅助理解的手段，吃透架构才是目的。

以下是 ShopAgent-X 的六个核心亮点。README 中有简要描述，这里展开讲解每个亮点的设计动机、核心机制和面试要点。

---

**【亮点一】五层可编排 Agent 架构**

将 AI 服务拆分为 Orchestrator、Planner、Executor、State、Events + Policies 五层，支持简单链路直接分发与复杂链路完整编排两种模式。

```
五层职责：

  RouterAgent（路由层）
  ├── 意图识别：LLM 分类 + 8 类意图
  ├── 复杂度判断：简单 / 中等 / 复杂
  └── 分发决策：简单链路直接分发，复杂链路走编排

  Orchestrator Layer（编排层）
  ├── 创建 state、调用 planner、逐步执行
  ├── 文件：agent/orchestrator.py
  └── 职责：流程编排，不直接调用 LLM，不直接操作存储

  Planner Layer（规划层）
  ├── 意图识别、步骤规划、问题改写、充分性判断
  ├── 文件：agent/planner.py
  └── 职责：只做决策，不执行具体操作

  Executor Layer（执行层）
  ├── 根据 step_type 分发到具体实现
  ├── 文件：agent/executor.py
  └── 职责：只执行，不决策

  State + Events Layer（状态与事件层）
  ├── AgentState 管理 run_id/trace_id/step 追踪
  ├── EventBus 事件驱动 + MetricsCollector 监控
  ├── 文件：agent/state.py, agent/events.py
  └── 职责：状态管理与全链路可观测
```

**设计动机**：电商导购场景下，简单请求（"推荐跑鞋"）和复杂请求（多步知识问答）的处理逻辑差异巨大。如果所有请求都走完整编排，简单请求会增加 2-3 倍延迟。分层后可以按复杂度选择执行路径。

**面试要点**：
- 为什么分五层？→ 因为 Agent 的核心流程恰好是五个关注点：编排、规划、执行、状态、事件
- 简单链路和复杂链路怎么选择？→ RouterAgent 根据意图和复杂度判断，简单请求直接分发，复杂请求走 Orchestrator 编排
- 某一层出问题怎么处理？→ 比如 LLM 超时只影响 Tool 层，通过重试策略处理

---

**【亮点二】9 个专业 Agent 多 Agent 协作机制**

RouterAgent 负责意图分类和任务分发，9 个专业 Agent 独立工作，通过 ToolRegistry 共享工具能力。

```
Agent 类型与职责：

  RouterAgent（中央路由）
  ├── 意图识别：8 类意图（shopping/knowledge/chitchat/cart/admin/reasoning/photo/comparison）
  ├── 准确率：88.9%
  └── 统一记忆写入入口

  ShoppingAgent（购物导购）⭐ 核心
  ├── 四级搜索流水线：排除词 → 关键词 → 精确匹配 → 泛词展开
  ├── 同义词展开 + 精确匹配 + 反选排除
  ├── 购物车管理：对话式加购/删除/批量操作
  └── 1700+ 行核心逻辑

  KnowledgeQAAgent（知识问答）
  ├── L1 直答（简单问题，~2s）
  ├── L2 检索（中等问题，~4s）
  └── L3 Orchestrator 编排（复杂问题，~8s）

  ChitChatAgent（闲聊）
  └── 带会话记忆的闲聊回复

  ReasoningAgent（复杂推理）
  └── 多步推理链路

  AdminCopilotAgent（管理助手）
  ├── OpsAgent（运营分析）
  └── InspectionAgent（知识巡检）

  RetrievalAgent（检索增强）
  └── 问题改写 + 多路召回 + 重排序
```

**设计动机**：电商场景下，不同任务的处理逻辑差异很大——商品推荐需要四级搜索流水线，闲聊 2 秒搞定，知识问答需要检索增强。分开后每个 Agent 只做一件事，可以独立优化。ShoppingAgent 作为核心 Agent 有 1700+ 行代码，如果和其他逻辑混在一起根本无法维护。

**面试要点**：
- 为什么不把所有逻辑放一个 Agent 里？→ 职责分离，降低复杂度，独立优化。ShoppingAgent 的四级搜索流水线和其他 Agent 逻辑完全不同
- 意图分类怎么做到 88.9% 准确率？→ LLM 分类 + 关键词 Fallback，8 类意图覆盖电商全场景
- 新增 Agent 难吗？→ 不难，继承 BaseAgent 实现 handle() 方法，在 RouterAgent 注册即可

---

**【亮点三】统一 Tool Registry 工具体系**

将知识检索、OCR、购物车、会话记忆等 AI 能力抽象为标准 Tool，统一管理。

```
Tool 体系设计：

  Tool 基类（tools/base.py）
  ├── name: str              → 工具名称
  ├── description: str       → 工具描述
  ├── input_schema           → 输入参数 Schema
  ├── metadata: ToolMetadata → 超时、重试次数
  ├── validate_input()       → 输入参数校验
  └── execute()              → 执行逻辑

  ToolRegistry（tools/registry.py，单例）
  ├── register_tool(tool)    → 注册工具
  ├── invoke_tool(name, params) → 调用（带超时 + 重试 + 追踪）
  ├── 共享线程池 ThreadPoolExecutor(max_workers=10)
  └── get_all_tools()        → 列出所有工具

  已注册工具（7 个）：
  ├── knowledge_search  → 知识库语义检索（30s 超时）
  ├── question_rewrite  → 问题改写 + 画像感知
  ├── rerank            → 检索结果重排序
  ├── memory_read       → 读取会话记忆
  ├── memory_write      → 写入会话记忆（RouterAgent 统一入口）
  ├── ocr_extract       → 图片文字提取
  └── cart_tool         → 购物车 CRUD
```

**设计动机**：AI 能力越来越多（检索、OCR、购物车、记忆...），如果没有统一管理，每个 Agent 自己调用会很混乱。Tool Registry 把所有能力标准化，新增工具只需要继承基类并注册。共享线程池避免每个工具单独创建线程的开销。

**面试要点**：
- 为什么要抽象 Tool 层？→ 统一接口、可插拔、可监控、可复用
- 超时怎么做的？→ 共享 ThreadPoolExecutor 强制超时，防止一个卡住的请求阻塞整个 Agent
- 执行追踪有什么用？→ ToolExecutionTracker 记录每次调用的参数、结果、耗时，TTL 3600s 自动淘汰

---

**【亮点四】电商场景 RAG 全链路优化**

以商品推荐为例，实现从用户问题到商品卡片的端到端闭环，包含四级搜索流水线和三层防幻觉机制。

```
商品推荐执行流程：

  用户提问："推荐跑鞋"
    │
    ▼
  ① RouterAgent 意图识别
    └── 意图=shopping → 分发给 ShoppingAgent
    │
    ▼
  ② 会话记忆读取
    └── 读取历史对话，理解上下文（"要轻量的"、"不要超过五百"）
    │
    ▼
  ③ FAISS 向量检索
    └── 从商品知识库中语义检索相关商品
    │
    ▼
  ④ 四级搜索流水线 ⭐ 核心
    ├── Step 1: LLM 提取排除词（"不要优衣库" → ["优衣库"]）
    ├── Step 2: LLM + 正则提取搜索关键词（双保险）
    ├── Step 3: 三级优先级匹配
    │    精确匹配: "跑鞋" → 只匹配"跑步鞋"，不展开
    │    泛词展开: "鞋子" → ["篮球鞋","跑步鞋","徒步鞋","运动鞋"]
    │    拆字匹配: 中文单字拆分模糊搜索
    ├── Step 4: SQL 参数化搜索 + 品类过滤 + 排除词过滤
    └── Step 5: 后处理 → 去重 + 状态过滤 + 加权排序（评分60% + 销量40%）
    │
    ▼
  ⑤ LLM 生成推荐话术
    └── 基于商品信息生成个性化推荐文案
    │
    ▼
  ⑥ 三层防幻觉校验 ⭐ 核心
    ├── Prompt 约束："不要编造价格/评分/销量数据"
    ├── 输出校验：validateProductCards() 比对数据库
    └── 参数化查询：SQL 注入防护
    │
    ▼
  ⑦ 构建商品卡片 JSON → SSE 流式返回
```

**设计动机**：电商场景对准确性要求极高——用户说"推荐跑鞋"，如果返回篮球鞋就是灾难。四级搜索流水线解决了"拆字误匹配"问题，三层防幻觉确保 LLM 不会编造商品信息。

**面试要点**：
- 四级搜索流水线怎么设计的？→ 排除词→关键词→精确匹配→泛词展开，优先级从高到低，精确匹配不展开
- 防幻觉怎么做的？→ 三层：Prompt 约束 + 输出校验（比对数据库）+ 参数化查询
- 多轮对话怎么处理？→ 会话记忆 + 上下文理解，支持渐进式需求收敛

---

**【亮点五】多模态交互接入**

支持拍照识图、语音输入等多模态交互，通过豆包 Vision API 和 ASR API 实现。

```
多模态交互链路：

  拍照识图：
    Android 拍照/选图
      → Java 上传图片 → Python 调用豆包 Vision API
      → 返回描述（如"一双白色Nike运动鞋"）
      → 作为搜索词走 ShoppingAgent 检索链路
      → 返回推荐商品 + 商品卡片

  语音输入：
    Android 长按录音 → 音频文件
      → Java 透传 → Python 调用豆包 ASR API
      → 返回文本 → 作为普通消息走对话链路

  用户画像感知：
    注册时收集用户偏好（性别/肤质/偏好标签）
      → 推荐时自动适配
      → 油皮用户搜洗面奶 → 优先推荐控油款
      → 推荐话术自动调整称呼（兄弟/姐妹）
```

**设计动机**：电商场景下，用户可能不知道怎么描述需求（"这个东西叫什么"），拍照识图解决了这个问题。语音输入则提升了移动端的交互体验。

**面试要点**：
- 拍照识图怎么实现的？→ 豆包 Vision API 理解图片语义 → 提取商品特征 → 走 ShoppingAgent 检索链路
- 多模态和普通对话有什么区别？→ 前端多一步图片/音频上传，Python 服务多一步 API 调用，后续流程完全一样
- 用户画像怎么影响推荐？→ 注册时收集偏好，推荐时作为上下文注入 LLM Prompt

---

**【亮点六】全链路可观测性设计**

系统内置 runId/traceId 全链路追踪，通过 EventBus 事件驱动机制实现完整可观测性。

```
事件驱动追踪（agent/events.py）：

  事件类型：
  ├── RunStartedEvent    → Agent 开始执行
  ├── StepStartedEvent   → 步骤开始
  ├── StepCompletedEvent → 步骤完成（含耗时、输出）
  ├── StepFailedEvent    → 步骤失败（含错误信息）
  ├── RunCompletedEvent  → Agent 执行完成
  └── RunFailedEvent     → Agent 执行失败

  每个事件携带：
  ├── run_id: 唯一标识一次 Agent 执行
  ├── trace_id: 跨服务追踪（Android → Java → Python）
  ├── step_id: 步骤标识
  ├── step_type: 步骤类型
  ├── duration_ms: 执行耗时
  └── output/error: 输出或错误信息

  MetricsCollector 监控：
  ├── 工具调用成功率
  ├── 平均响应时间
  ├── 错误率统计
  └── 管理端仪表盘展示
```

**设计动机**：Agent 的执行是多步骤的，如果没有追踪机制，出了问题很难定位。EventBus 事件驱动让每个步骤都有记录，管理端可以回溯整个执行过程。

**面试要点**：
- 为什么用 EventBus 而不是直接记录日志？→ 解耦事件产生者和消费者，支持多种监听方式
- runId 和 traceId 的区别？→ runId 标识一次 Agent 执行，traceId 跨服务追踪（Android → Java → Python）
- 管理端能看到什么？→ Agent 执行记录、步骤详情、工具调用详情、全链路时间线

---

## 3. 前置知识清单

### 必须掌握（不学看不懂代码）

```
Java 基础
├── 面向对象（继承、多态、接口）
├── 集合框架（List、Map、Set）
├── 异常处理（try-catch、自定义异常）
└── Spring Boot 基础（Controller、Service、依赖注入）

Python 基础
├── 基本语法（函数、类、装饰器）
├── 类型注解（typing 模块）
├── dataclass 数据类
└── 异步编程基础（async/await 了解即可）

数据库基础
├── MySQL 基本 CRUD
├── Redis 缓存基本概念
└── 了解向量数据库是什么（不需要会用）

Android 基础（了解即可）
├── Kotlin 基本语法
├── Jetpack Compose 声明式 UI
└── HTTP 请求基础
```

### 建议掌握（学了更好理解）

```
设计模式
├── 单例模式（ToolRegistry 用了）
├── 策略模式（检索策略、缓存策略）
├── 观察者模式（EventBus 事件总线）
├── 注册器模式（Tool 注册机制）
└── 建造者模式（AgentState 构建）

AI 基础
├── LLM 是什么（大语言模型基本概念）
├── RAG 是什么（检索增强生成）
├── Embedding 是什么（向量化）
├── Prompt Engineering 基础
└── 了解 Agent = LLM + Tools + Memory + Planning
```

### 不需要掌握（项目里用到了但可以跳过）

```
├── Milvus 向量数据库（项目默认用 FAISS，零依赖）
├── Docker 部署（学习阶段本地跑就行）
├── LangChain 内部实现（项目只用了很小一部分）
└── Cohere Rerank（可选组件，不影响核心理解）
```

---

## 4. 和 LangGraph 的区别与迁移能力

> **面试时的策略**：不需要主动介绍和 LangGraph 的区别，这会显得你在刻意比较。正确的做法是：先讲清楚自己项目的架构设计和亮点，如果面试官主动问到 LangGraph 或者提到"你为什么不用框架"，再展开讲解区别，突出本项目的设计思想和你自己的理解。主动讲 = 刻意，被动答 = 有深度。

### 区别

一句话：**ShopAgent-X 的核心设计思想和 LangGraph 相似（状态图 + 节点编排 + 条件路由），但没有用 LangGraph，全部手写实现。**

- 用 LangGraph → 你会调 API（`StateGraph`、`add_node`、`add_edge`）
- 用 ShopAgent-X → 你会造 API（自己实现状态机、编排器、工具注册）

**概念对照表：**

```
LangGraph 的概念              ShopAgent-X 的对应实现               文件位置
────────────────────────────────────────────────────────────────────────────
State Graph（状态图）      →  AgentState + StepType 状态机        agent/state.py
Node（节点）               →  Executor.execute_step()             agent/executor.py
Edge（边/条件路由）        →  Planner.plan_steps() +              agent/planner.py
                              should_terminate()
Tool Node（工具节点）      →  ToolRegistry.invoke_tool()          tools/registry.py
Memory（记忆）             →  Redis 会话记忆 + MySQL 用户记忆     tools/memory_read.py
Checkpoint（检查点）       →  EventBus 事件追踪 + runId/traceId   agent/events.py
Multi-Agent（多智能体）    →  RouterAgent + 8 个专业 Agent        workflows/*.py
Command（节点返回值）      →  step.output_data                     agent/state.py
Conditional Edge           →  RouterAgent 意图分类 + 复杂度判断   workflows/router_agent.py
```

### 迁移能力——学完后你能做什么

这是项目最核心的价值：**学完后，你可以在任何项目中从零构建适合的 Agent，而不是只会调框架。**

```
框架使用者的能力边界：
  会用 LangGraph → 只能在 LangGraph 的 API 范围内工作
  会用 AutoGen  → 只能在 AutoGen 的框架下工作
  遇到新框架    → 重新学 API

ShopAgent-X 学习者的能力边界：
  理解状态机原理     → 任何需要状态管理的场景都能设计
  理解任务编排原理   → 任何需要流程编排的场景都能实现
  理解工具注册原理   → 任何需要能力扩展的场景都能抽象
  理解事件驱动原理   → 任何需要追踪监控的场景都能设计
  理解 RAG 全链路    → 任何需要检索增强的场景都能实现
  遇到新框架         → 看源码就知道它怎么实现的，因为原理你都懂
```

**面试时这样说：**
> "我这个项目最大的价值不是实现了一个电商导购系统，而是通过自己手写 Agent 的底层原理，获得了在任何项目中从零构建 Agent 的能力。我不依赖任何 Agent 框架，我理解框架背后的设计原理——状态机怎么管理、任务怎么编排、工具怎么注册、事件怎么驱动、RAG 怎么优化。这意味着不管未来出现什么新的 Agent 框架，我都能快速看懂它的源码，因为原理是相通的。"

---

## 5. 分阶段学习路线

### 第一阶段：跑通项目（1-2 天）

```
目标：把项目跑起来，理解用户看到的是什么

步骤：
1. 按 README 的"快速开始"跑通项目
2. Android 端：问一个问题，看商品推荐效果
3. 管理端：看仪表盘、Agent 执行记录
4. 试试商品推荐、闲聊、知识问答的区别

验证：能用 Android 访问系统，问一个问题得到商品推荐
```

### 第二阶段：理解数据流（3-5 天）

```
目标：一个请求从前端到后端到 AI 服务的完整链路

读代码顺序（跟着请求走）：
1. Android 发请求     → android/app/src/main/java/.../data/api/
2. Java 后端处理      → controller/ChatController.java
                       → service/ChatService.java
                       → service/impl/ChatServiceImpl.java
3. Python AI 处理     → api/routes.py
                       → workflows/router_agent.py（意图分类）
                       → workflows/shopping_agent.py（商品推荐）
4. 数据返回           → Python SSE → Java 透传 → Android 逐字渲染

验证：能在代码中找到一个请求从发起到返回的完整路径
```

### 第三阶段：吃透 Agent 核心（5-7 天）⭐ 重点

```
目标：理解 Agent 的五个核心组件

Day 1-2: RouterAgent（路由 Agent）    → workflows/router_agent.py
  重点：意图分类、复杂度判断、统一分发

Day 3-4: ShoppingAgent（购物导购）    → workflows/shopping_agent.py
  重点：四级搜索流水线、防幻觉校验、商品卡片构建

Day 5: Orchestrator + Planner         → agent/orchestrator.py, agent/planner.py
  重点：编排流程、步骤规划、重试机制

Day 6: ToolRegistry + Tools           → tools/registry.py, tools/base.py
  重点：单例模式、注册机制、超时重试、执行追踪

Day 7: State + Events                 → agent/state.py, agent/events.py
  重点：状态管理、事件驱动、全链路追踪

验证：能不看代码画出 Agent 执行流程图
```

### 第四阶段：理解多 Agent 协作（3-5 天）⭐ 重点

```
Day 1: KnowledgeQAAgent               → workflows/knowledge_qa_agent.py
  重点：三级复杂度（L1/L2/L3）、检索增强

Day 2: ChitChatAgent + ReasoningAgent → workflows/chitchat_agent.py
                                         workflows/reasoning_agent.py

Day 3: AdminCopilotAgent              → workflows/admin_copilot_agent.py
  重点：OpsAgent + InspectionAgent 子 Agent 协作

Day 4-5: 意图分类器                   → intent/classifier.py
  重点：8 类意图、LLM 分类 + 关键词 Fallback

验证：能画出完整的多 Agent 协作流程图
```

### 第五阶段：理解工程实践（2-3 天）

```
Day 1: 安全与认证  → config/SecurityConfig.java, common/JwtUtil.java
Day 2: 缓存策略    → service/CacheService.java, MultiLevelCacheServiceImpl.java
Day 3: 数据库设计  → sql/init.sql（25 张表）

验证：能解释项目的缓存策略和安全机制
```

---

## 6. 核心代码逐行解读

### 6.1 Orchestrator — Agent 的大脑

```python
# agent/orchestrator.py 核心流程（简化版）

class Orchestrator:
    def run(self, input_text, ...):
        # ① 创建状态（包含 runId、traceId）
        state = self.create_state(input_text, ...)

        # ② 输入验证（策略引擎）
        is_valid, error_msg = self.policies.validate_input(input_text)
        if not is_valid:
            state.fail(error_msg)
            return self._build_error_response(state)

        # ③ 规划步骤（Planner 决定做什么）
        planned_steps = self.planner.plan_steps(state)
        state.planned_steps = planned_steps

        # ④ 逐步执行（Executor 执行每一步）
        for step_name in planned_steps:
            step = state.add_step(step_type, step_name)
            self.event_bus.publish(StepStartedEvent(...))

            # ⑤ 带重试的执行
            while not step_done:
                try:
                    self.executor.execute_step(state, step)
                    step_done = True
                except Exception as e:
                    if self.policies.should_retry(retry_count, e):
                        retry_count += 1
                    else:
                        state.fail(str(e))
```

**面试要点**：
- 为什么用 Orchestrator 模式？→ 解耦规划和执行
- 重试策略怎么设计的？→ policies 决定，支持配置
- 事件发布有什么用？→ 全链路追踪、监控、调试

### 6.2 RouterAgent — 请求分发中心

```python
# workflows/router_agent.py 核心逻辑

class RouterAgent:
    def handle(self, message, ...):
        # ① 意图分类（LLM + 关键词 Fallback）
        intent_result = self.classifier.classify(message)

        # ② 根据意图分发到对应 Agent
        if intent_result.intent == IntentType.SHOPPING:
            agent = ShoppingAgent()
        elif intent_result.intent == IntentType.KNOWLEDGE:
            agent = KnowledgeQAAgent()
        elif intent_result.intent == IntentType.CHITCHAT:
            agent = ChitChatAgent()
        # ... 其他意图

        # ③ 调用子 Agent 处理
        response = agent.handle(message, ...)

        # ④ 统一记忆写入
        self._write_memory(message, response, ...)

        return response
```

**面试要点**：
- 意图分类怎么做的？→ LLM 分类 + 关键词 Fallback，88.9% 准确率
- 为什么统一在 RouterAgent 写记忆？→ 避免每个 Agent 重复写入，保证一致性
- 新增意图难吗？→ 不难，在 IntentClassifier 添加类型，在 RouterAgent 添加分发逻辑

### 6.3 ShoppingAgent — 电商导购核心

```python
# workflows/shopping_agent.py 核心逻辑（简化版）

class ShoppingAgent:
    def handle(self, message, ...):
        # ① 读取会话记忆
        memory = self._read_memory(conversation_id)

        # ② 向量检索相关商品知识
        search_results = self._vector_search(message)

        # ③ 四级搜索流水线 ⭐
        products = self._search_products(message, memory)

        # ④ LLM 生成推荐话术
        recommendation = self._generate_recommendation(message, products)

        # ⑤ 三层防幻觉校验 ⭐
        validated_cards = self._validate_product_cards(products)

        # ⑥ 构建商品卡片 JSON
        return AgentResponse(
            text=recommendation,
            product_cards=validated_cards
        )

    def _search_products(self, message, memory):
        # Step 1: LLM 提取排除词
        exclude_words = self._extract_exclude_words(message)

        # Step 2: LLM + 正则提取搜索关键词
        keywords = self._extract_keywords(message)

        # Step 3: 三级优先级匹配
        # 精确匹配 → 泛词展开 → 拆字匹配

        # Step 4: SQL 参数化搜索 + 品类过滤 + 排除词过滤

        # Step 5: 后处理 → 去重 + 状态过滤 + 加权排序
        return products
```

**面试要点**：
- 四级搜索流水线怎么设计的？→ 排除词→关键词→精确匹配→泛词展开
- 防幻觉怎么做的？→ Prompt 约束 + 输出校验（比对数据库）+ 参数化查询
- 购物车怎么管理？→ 对话式加购/删除，30s 会话缓存

### 6.4 ToolRegistry — 能力管理中心

```python
# tools/registry.py（简化版）

class ToolRegistry:  # 单例模式
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
                    cls._instance._tools = {}
                    cls._instance._executor = ThreadPoolExecutor(max_workers=10)
        return cls._instance

    def invoke_tool(self, tool_name, parameters, run_id):
        tool.validate_input(parameters)           # ① 验证参数
        tool_call_id = tracker.start_tool_call()  # ② 执行追踪

        future = self._executor.submit(tool.execute, parameters)  # ③ 共享线程池
        result = future.result(timeout=timeout_sec)               # ④ 超时控制

        tracker.end_tool_call(tool_call_id)       # ⑤ 记录日志
```

**面试要点**：
- 为什么用单例？→ 全局只需要一个注册器，共享线程池
- 超时怎么做的？→ 共享 ThreadPoolExecutor 强制超时
- 执行追踪有什么用？→ 管理端可以看到每个工具的调用详情

---

## 7. 如何验证执行流程

### 方式一：看管理端 Agent 执行记录（最直观）

```
1. 启动项目
2. Android 端问一个问题，比如"推荐跑鞋"
3. 打开管理端 → Agent 执行记录页面
4. 你会看到每个步骤的详情：意图识别、商品搜索、推荐生成、
   防幻觉校验，以及每个步骤的耗时
```

### 方式二：看 Python AI 服务日志（最详细）

```
启动 Python AI 服务后，问一个问题，终端会输出：

  [RouterAgent] intent=shopping, confidence=0.92
  [ShoppingAgent] keywords=['跑鞋'], exclude=[]
  [ShoppingAgent] search: exact_match=2, fuzzy_match=3, total=5
  [ToolRegistry] invoking tool: memory_read, completed in 12ms
  [ToolRegistry] invoking tool: knowledge_search, completed in 156ms
  [LLM] generating recommendation with 5 products
  [validateProductCards] 5 cards validated, 0 overridden
  [ShoppingAgent] completed in 2341ms
```

### 方式三：写测试验证（最严谨）

```python
# tests/test_agent_flow.py

def test_shopping_flow():
    """验证商品推荐的完整执行流程"""
    from workflows.shopping_agent import ShoppingAgent
    agent = ShoppingAgent()
    result = agent.handle(message="推荐跑鞋", user_id="test-user")

    assert result.text  # 有推荐话术
    assert result.product_cards  # 有商品卡片
    assert len(result.product_cards) > 0

def test_router_intent():
    """验证意图分类"""
    from workflows.router_agent import RouterAgent
    router = RouterAgent()

    # 商品推荐
    result = router.handle(message="推荐跑鞋")
    assert "product_cards" in str(result)

    # 闲聊
    result = router.handle(message="你好")
    assert "product_cards" not in str(result)

def test_tool_registry():
    """验证工具注册和调用"""
    from tools.registry import tool_registry
    assert tool_registry.has_tool("knowledge_search")
    assert tool_registry.has_tool("memory_read")
    assert tool_registry.has_tool("cart_tool")
```

### 方式四：用 curl 直接调用 API

```bash
# 普通对话
curl -X POST http://localhost:8000/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "推荐跑鞋", "user_id": "test-user"}'

# 图片识别
curl -X POST http://localhost:8000/api/recognize-image \
  -F "file=@shoe.jpg"

# 返回包含完整的执行信息：intent、products、recommendation、product_cards
```

---

## 8. 面试高频问题与回答模板

### Q1: 介绍一下你的项目

```
ShopAgent-X 是一个多模态电商导购 AI Agent 系统。
和市面上用框架搭建的项目不同，我自己实现了 Agent 的底层工作原理，
包括编排器、规划器、执行器、工具注册器、事件总线等核心组件。

架构上分为三端：Android App（Kotlin + Jetpack Compose）、
Spring Boot 后端、Python AI 微服务。
AI 服务内部采用五层架构：RouterAgent → Orchestrator → Planner
→ Executor → State + Events。

系统支持 9 个专业 Agent 协作，RouterAgent 根据意图识别将请求分发到
商品导购、知识问答、闲聊等不同 Agent。商品导购还设计了四级搜索流水线
和三层防幻觉机制，确保推荐结果的准确性。
```

### Q2: Agent 的执行流程是怎样的

```
以商品推荐为例：

1. 用户提问进入 RouterAgent
2. RouterAgent 进行意图识别（LLM + 关键词 Fallback）
3. 意图=shopping，分发给 ShoppingAgent
4. ShoppingAgent 执行流程：
   - 读取会话记忆
   - FAISS 向量检索相关商品知识
   - 四级搜索流水线（排除词→关键词→精确匹配→泛词展开）
   - LLM 生成推荐话术
   - 三层防幻觉校验
   - 构建商品卡片 JSON
5. SSE 流式返回给客户端

关键设计决策：
- 简单链路直接分发 → 避免编排开销，~2s 响应
- 复杂链路走 Orchestrator 编排 → 保证错误重试、状态追踪
- 统一记忆写入 → RouterAgent 统一入口，避免重复写入
```

### Q3: 为什么自己实现而不直接用框架

```
核心原因：获得迁移能力。

1. 用框架你会调 API，自己实现你会造 API
   用 LangGraph 你会 StateGraph、add_node、add_edge
   自己实现你会状态机、编排器、工具注册、事件驱动
   前者只能在框架内工作，后者可以在任何项目中构建 Agent

2. 原理是相通的，框架会变但原理不变
   LangGraph 用状态图，AutoGen 用消息传递，CrewAI 用角色扮演
   但底层都是：状态管理 + 任务编排 + 工具调用 + 记忆管理
   理解了原理，任何新框架都能快速上手

3. 实际工作中经常需要定制
   框架做不了的事，你得自己做
   比如：四级搜索流水线、三层防幻觉、电商场景定制
   有了底层原理的理解，你可以根据业务需求定制任何 Agent 行为
```

### Q4: 遇到了什么困难，怎么解决的

```
1. RAG 检索精度问题
   问题：用户说"推荐跑鞋"，拆字逻辑把"跑"和"鞋"单独匹配，
         导致篮球鞋也混入结果
   解决：四级搜索流水线——排除词→关键词→精确匹配→泛词展开
         精确匹配不展开，避免语义漂移

2. LLM 幻觉问题
   问题：LLM 编造不存在的商品、价格、评分
   解决：三层防幻觉——Prompt 约束 + 输出校验（比对数据库）+ 参数化查询

3. 多模态接入问题
   问题：拍照识图返回的描述太泛，搜索结果不精准
   解决：豆包 Vision API 提取商品特征 → 作为精确搜索词走四级流水线

4. 意图分类准确率问题
   问题：初期意图分类只有 70% 准确率
   解决：LLM 分类 + 关键词 Fallback，8 类意图覆盖电商全场景，
         准确率提升到 88.9%
```

### Q5: 项目有什么亮点

```
六大亮点：

【亮点一】五层可编排 Agent 架构
  支持简单链路直接分发与复杂链路完整编排两种模式，
  简单请求 ~2s 响应，复杂请求 ~8s 响应。

【亮点二】9 个专业 Agent 多 Agent 协作
  RouterAgent 意图分类 88.9% 准确率，
  ShoppingAgent 1700+ 行核心逻辑。

【亮点三】统一 Tool Registry 工具体系
  7 个已注册工具，共享线程池，超时重试，执行追踪。

【亮点四】电商场景 RAG 全链路优化
  四级搜索流水线 + 三层防幻觉机制。

【亮点五】多模态交互接入
  拍照识图（豆包 Vision API）+ 语音输入（ASR API）。

【亮点六】全链路可观测性设计
  EventBus 事件驱动，runId/traceId 贯穿，管理端可视化。

核心卖点：
  不是"会用框架"，而是"会造框架"。
  学完这个项目，你获得了在任何项目中从零构建 Agent 的迁移能力。
```

### Q6: 四级搜索流水线是怎么设计的

```
问题背景：用户说"推荐跑鞋"，如果简单拆字匹配，"跑"和"鞋"
会被分开匹配，导致篮球鞋也混入结果。

四级搜索流水线：

Step 1: LLM 提取排除词
  用户说"不要优衣库" → LLM 提取 ["优衣库"]
  后续搜索过滤掉匹配商品

Step 2: LLM + 正则提取搜索关键词（双保险）
  LLM 提取主要关键词，正则作为 Fallback

Step 3: 三级优先级匹配
  精确匹配: "跑鞋" → 只匹配"跑步鞋"，不展开（最高优先级）
  泛词展开: "鞋子" → ["篮球鞋","跑步鞋","徒步鞋","运动鞋"]
  拆字匹配: 中文单字拆分模糊搜索（最低优先级）

Step 4: SQL 参数化搜索 + 品类过滤 + 排除词过滤

Step 5: 后处理 → 去重 + 状态过滤 + 加权排序（评分60% + 销量40%）

设计决策：
  精确匹配不展开是关键——"跑鞋"就是"跑步鞋"，不应该包含篮球鞋
  三级优先级确保精确结果优先返回
```

---

## 9. 踩坑与经验教训

以下是项目从开发至今 40+ 次提交中提炼出的真实问题。

### RAG 检索踩坑

```
拆字误匹配
  问题：用户说"推荐跑鞋"，拆字逻辑把"跑"和"鞋"单独匹配，
        导致篮球鞋也混入结果
  教训：中文分词不能简单拆字，需要精确匹配优先 + 泛词展开兜底

向量检索不精准
  问题：FAISS 向量检索返回的结果和用户意图不匹配
  教训：向量检索只是候选召回，还需要精确匹配和排序优化

排除词无效
  问题：用户说"不要优衣库"，但排除词没有生效
  教训：LLM 提取排除词需要明确的 Prompt 指令 + 正则兜底
```

### AI 服务质量踩坑

```
LLM 幻觉
  问题：LLM 编造不存在的商品、价格、评分
  教训：电商场景对准确性要求极高，必须有防幻觉机制

意图分类不准
  问题：用户说"推荐一下"，被分类为 chitchat 而不是 shopping
  教训：意图分类需要 LLM + 关键词双重保障，单一方式不够

推荐话术单一
  问题：每次推荐的话术都一样，用户体验差
  教训：LLM Prompt 需要多样化指令，注入用户画像增加个性化
```

### 架构演进踩坑

```
所有请求都走编排
  问题：简单请求也走 Orchestrator 编排，延迟增加 2-3 倍
  教训：按复杂度分层，简单请求直接分发，复杂请求走编排

记忆写入分散
  问题：每个 Agent 自己写记忆，逻辑不一致
  教训：统一在 RouterAgent 写记忆，避免重复和不一致

工具超时未设置
  问题：Tool 调用 LLM 时没有超时，一个卡住的请求阻塞整个 Agent
  教训：所有外部调用必须有超时，共享线程池强制超时控制
```

### Vibe Coding 的 8 条铁律

| # | 铁律 |
|---|------|
| 1 | 安全审查必须人工做 — AI 不会主动考虑 SQL 注入、缓存键隔离 |
| 2 | 异常处理必须明确要求 — AI 默认"抛出异常"，你需要"友好降级" |
| 3 | 发现重复立即抽取 — AI 喜欢复制代码到多个文件 |
| 4 | 所有外部调用必须有超时 — LLM 调用天然不稳定 |
| 5 | 记忆系统提前规划 — Agent 记忆比你想象的复杂 |
| 6 | 外部依赖必须有降级 — FAISS 挂了不能整个系统不可用 |
| 7 | 配置文件不能硬编码 — 密钥用环境变量 |
| 8 | 意图识别是命脉 — 分类不准 = 用户体验灾难 |

---

## 10. 学后收获与扩展建议

### 技术能力收获

```
✅ 理解 Agent 底层原理 — 不是"会用框架"，而是"会造框架"
✅ 掌握设计模式实战 — 单例、策略、观察者、注册器、建造者
✅ 理解 RAG 完整流程 — 意图识别→改写→检索→重排序→生成→防幻觉
✅ 掌握全栈开发能力 — Android + Spring Boot + FastAPI
✅ 理解工程最佳实践 — 安全、缓存、监控、部署
```

### 面试能力收获

```
✅ 能讲清楚项目架构 — 三端架构、五层 Agent、多 Agent 协作
✅ 能回答深挖问题 — 设计决策、踩坑经历、优化方案
✅ 能展示迁移能力 — "我在任何项目中都能从零构建 Agent"
```

### 扩展学习建议

```
1. 对比学习其他 Agent 框架
   - LangChain Agent / AutoGen / CrewAI
   - 有了 ShopAgent-X 的基础，学这些框架会非常快

2. 阅读 Dify 源码
   - Dify 是生产级的 Agent 平台
   - 对比 Dify 的实现和 ShopAgent-X 的实现

3. 阅读 Claude Code 源码
   - 本项目部分设计借鉴了 Claude Code 的实现方式
   - 重点阅读：
     ├── 工具注册与调用机制 → 对比 ShopAgent-X 的 ToolRegistry
     ├── 执行状态管理 → 对比 ShopAgent-X 的 AgentState
     ├── 事件驱动架构 → 对比 ShopAgent-X 的 EventBus
     ├── 错误处理与重试策略 → 对比 ShopAgent-X 的 policies
     └── 上下文管理 → 对比 ShopAgent-X 的会话记忆
   - Claude Code 的源码质量很高，是学习 Agent 工程化的优秀参考

4. 给开源项目贡献 PR
   - Dify、LangChain、AutoGen 等项目都有 good first issues

5. 自己动手扩展功能
   - 新增一个 Agent 类型（比如客服 Agent）
   - 新增一个 Tool（比如网页搜索工具）
   - 支持更多多模态输入（比如视频理解）
```

### 推荐阅读

```
书籍：
├──《Designing Data-Intensive Applications》— 数据密集型系统设计
├──《Designing Machine Learning Systems》— ML 系统设计
└──《Building LLM Powered Applications》— 构建 LLM 应用

论文：
├── ReAct: Synergizing Reasoning and Acting in Language Models
├── Toolformer: Language Models Can Teach Themselves to Use Tools
└── Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks

博客：
├── Lilian Weng 的 Agent 博客 — 理论基础
└── LangChain 官方博客 — 行业实践
```

---

> **文档版本**：v1.0
> **更新日期**：2026-06-11
> **配合使用**：README.md（项目说明） + 本指南（学习路线）
