# RAG 多模态电商导购 AI Agent — 详细执行计划

> 项目路径：E:\Python_qb\DEmo\ShopAgent-X
> 基于 AgentCraft 复用约 60% 后端能力，新增电商导购业务层 + Android 原生客户端
> 原则：先跑通最小闭环，再逐步增强

---

## 一、当前现状盘点

| 模块 | 状态 | 待完成 |
|------|------|--------|
| Android App | 空壳模板（MainActivity + Theme） | 全部业务 UI + API 对接 |
| Python AI Agent | 从 AgentCraft 复制，架构完整 | 电商领域适配（Prompt/工具/Workflow） |
| Java 后端 | 从 AgentCraft 复制，通用 CRUD | 电商商品 API、推荐接口 |
| 前端管理后台 | React SPA，知识库管理等 | 可暂不改，或后续做商品管理页面 |
| 数据集 | 100 个商品 JSON + 图片（4 品类×25） | 入库 + 向量化 + 多模态索引 |
| SQL | AgentCraft 的建表脚本 | 新增商品表、推荐日志表 |
| Demo | 空 | APK、截图、演示视频 |
| Docs | 空 | 架构文档、README |

---

## 二、整体架构设计

```
┌─────────────────┐     HTTP/WebSocket      ┌──────────────────────┐
│  Android App    │ ◄──────────────────────► │  Java Spring Boot    │
│  Kotlin+Compose │                          │  (API Gateway + 业务)│
└─────────────────┘                          └──────────┬───────────┘
                                                        │ HTTP
                                             ┌──────────▼───────────┐
                                             │  Python AI Agent     │
                                             │  (FastAPI + LangGraph│
                                             │   式多Agent编排)      │
                                             └──────────┬───────────┘
                                                        │
                                    ┌───────────┬───────┼───────┬──────────┐
                                    ▼           ▼       ▼       ▼          ▼
                                 MySQL       Redis    FAISS   LLM API   多模态
                                (商品元数据)  (缓存)  (向量库) (大模型)  (图片理解)
```

---

## 三、分阶段执行计划

### Phase 0：环境搭建与数据准备（Day 1，预计 3-4 小时）

**目标：项目能跑起来，数据能入库**

- [ ] **0.1** Python 环境搭建
  - `cd backend/python-service && python -m venv venv && venv\Scripts\activate && pip install -r requirements.txt`
  - 确认 FAISS、FastAPI、uvicorn 等核心依赖安装成功
  - 配置 `.env` 文件（LLM API Key、MySQL、Redis 连接信息）

- [ ] **0.2** MySQL 数据库初始化
  - 执行 `backend/sql/init.sql` 建表
  - 新增商品表 `product` 和品类表 `category`：
    ```sql
    CREATE TABLE category (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        name VARCHAR(50) NOT NULL COMMENT '品类名称',
        icon_url VARCHAR(500) COMMENT '品类图标',
        sort_order INT DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE product (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        category_id BIGINT NOT NULL,
        name VARCHAR(200) NOT NULL COMMENT '商品名称',
        price DECIMAL(10,2) COMMENT '价格',
        original_price DECIMAL(10,2) COMMENT '原价',
        description TEXT COMMENT '商品描述',
        specs JSON COMMENT '规格参数',
        image_url VARCHAR(500) COMMENT '主图URL',
        tags VARCHAR(500) COMMENT '标签，逗号分隔',
        rating DECIMAL(3,2) DEFAULT 0 COMMENT '评分',
        sales_count INT DEFAULT 0 COMMENT '销量',
        embedding_status TINYINT DEFAULT 0 COMMENT '0未向量化 1已向量化',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_category (category_id),
        INDEX idx_embedding_status (embedding_status)
    );

    CREATE TABLE recommendation_log (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        user_id BIGINT,
        session_id VARCHAR(64),
        query TEXT COMMENT '用户原始问题',
        recommended_product_ids JSON COMMENT '推荐的商品ID列表',
        agent_reasoning TEXT COMMENT 'Agent推理过程',
        feedback TINYINT COMMENT '用户反馈 1满意 0不满意',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_session (session_id)
    );
    ```

- [ ] **0.3** 商品数据导入脚本
  - 写 `data/import_products.py`：读取 `data/ecommerce_agent_dataset/` 下的 JSON 文件
  - 解析商品信息（名称、价格、描述、规格、图片路径）
  - 批量插入 MySQL `product` 表
  - 同步插入 `category` 表（4 个品类）

- [ ] **0.4** 商品向量化
  - 基于 `core/vector_store.py` 和 `core/text_splitter.py`
  - 编写 `tools/product_indexer.py`：
    - 将商品 name + description + specs + tags 拼接为文本
    - 调用 Embedding 模型生成向量
    - 写入 FAISS 索引 `faiss_index/product_index.faiss`
    - 保存 ID 映射 `faiss_index/product_id_map.pkl`

- [ ] **0.5** 验证：确认数据可查、向量可检索
  - 启动 Python 服务，手动调用知识检索 API，确认能返回相关商品

---

### Phase 1：AI Agent 电商适配（Day 2-3，预计 8-10 小时）

**目标：Agent 能理解用户购物意图并推荐商品**

- [ ] **1.1** 新增电商意图分类
  - 修改 `intent/classifier.py`，新增电商意图类型：
    - `product_search` — 商品搜索/查询
    - `product_compare` — 商品对比
    - `product_recommend` — 个性化推荐
    - `order_inquiry` — 订单查询（可选）
    - `chitchat` — 闲聊
    - `complaint` — 售后/投诉

- [ ] **1.2** 新增电商专属工具
  - `tools/product_search.py` — 商品搜索（关键词 + 向量混合检索）
  - `tools/product_compare.py` — 多商品对比（传入商品ID列表，返回结构化对比表）
  - `tools/product_recommend.py` — 基于用户画像/历史的个性化推荐
  - `tools/image_understand.py` — 多模态：用户发图 → 理解图片内容 → 搜索相似商品
  - 在 `tools/registry.py` 中注册新工具

- [ ] **1.3** 新增电商导购 Workflow
  - 创建 `workflows/shopping_agent.py`：
    - 理解用户需求 → 意图分类 → 调用工具 → 组织推荐话术
    - 推荐策略：先品类定位 → 再筛选 Top-N → 结构化展示（名称/价格/亮点/对比）
    - 支持多轮对话：记住用户偏好、已看过的商品、预算区间等
  - 修改 `workflows/router_agent.py`，增加电商意图的路由

- [ ] **1.4** Prompt 工程
  - 编写电商导购系统提示词，要求 Agent：
    - 像专业导购一样自然对话，不要像搜索引擎
    - 推荐时给出理由（匹配需求点）
    - 支持追问、对比、换一批等交互
    - 多模态场景：用户发图片时，理解图片内容并推荐相似商品

- [ ] **1.5** 验证：用 curl/Postman 模拟对话
  - 测试场景：「推荐一款平价面霜」「这两款哪个好」「给我看看图片」「换个品类」
  - 确认 Agent 能正确路由、调用工具、返回推荐结果

---

### Phase 2：Java 后端 API 适配（Day 3-4，预计 4-5 小时）

**目标：Android 能通过统一 API 调用后端服务**

- [ ] **2.1** 新增商品相关 Controller + Service
  - `ProductController.java`：商品列表、详情、搜索、分类
  - `RecommendController.java`：获取推荐、用户反馈
  - 对应的 Service + Mapper + Entity

- [ ] **2.2** 对话 API 适配
  - 复用 `ChatController.java`，调整请求/响应格式
  - 新增字段：`productCards`（结构化商品卡片数据）
  - 支持图片上传（多模态输入）

- [ ] **2.3** WebSocket 实时对话
  - 复用 AgentCraft 的 SSE/WebSocket 对话机制
  - 确保 Android 端能实时接收 Agent 的流式回复

- [ ] **2.4** API 文档
  - 用 Swagger/Knife4j 生成接口文档，方便 Android 端对接

---

### Phase 3：Android 客户端开发（Day 4-7，预计 15-20 小时）

**目标：完成可交互的电商导购 App，核心体验流畅**

- [ ] **3.1** 项目架构搭建
  - 技术栈：Kotlin + Jetpack Compose + MVVM + Retrofit + Hilt
  - 依赖引入：Retrofit/OkHttp、Coil（图片加载）、Navigation Compose、Material3
  - 目录结构：
    ```
    com.evanyao.shopagent/
    ├── di/                  # Hilt 依赖注入
    ├── data/
    │   ├── api/             # Retrofit API 接口定义
    │   ├── model/           # 数据模型
    │   └── repository/      # 数据仓库层
    ├── ui/
    │   ├── chat/            # 对话页面（核心）
    │   ├── home/            # 首页（品类入口 + 推荐）
    │   ├── product/         # 商品详情
    │   └── theme/           # 主题（已有）
    ├── components/          # 通用 Composable
    │   ├── MessageBubble.kt
    │   ├── ProductCard.kt
    │   ├── CategoryChips.kt
    │   └── ImagePicker.kt
    └── utils/
    ```

- [ ] **3.2** 首页（HomeScreen）
  - 顶部：搜索栏
  - 中部：4 个品类卡片（美妆护肤 / 数码电子 / 服饰运动 / 食品生活）
  - 底部：AI 导购入口按钮（大按钮，引导用户开始对话）
  - 可选：热门推荐轮播

- [ ] **3.3** 对话页面（ChatScreen）— **最核心页面**
  - 消息列表：支持文本消息 + 商品卡片消息 + 图片消息
  - 商品卡片组件 `ProductCard`：
    - 商品图片 + 名称 + 价格 + 评分
    - 「查看详情」「加入对比」按钮
  - 输入区：文本输入 + 图片上传按钮 + 发送按钮
  - 流式回复：逐字显示 Agent 回复（SSE）
  - 快捷入口：「推荐好物」「帮我对比」「换一批」

- [ ] **3.4** 商品详情页（ProductDetailScreen）
  - 商品大图轮播
  - 价格、评分、销量
  - 规格参数
  - 「问 AI 关于这个商品」按钮 → 跳转对话页并带上下文

- [ ] **3.5** 多模态交互
  - 拍照/相册选图 → 上传到后端 → Agent 理解图片 → 返回推荐
  - 图片在对话气泡中展示

- [ ] **3.6** 网络层
  - Retrofit API 接口定义
  - 请求/响应模型
  - Token 管理（可简化，比赛不需要完整鉴权）
  - 错误处理 + Loading 状态

- [ ] **3.7** 验证：端到端跑通
  - Android → Java 后端 → Python Agent → 返回推荐 → Android 展示
  - 确认文本对话、商品推荐、图片上传三个场景都通

---

### Phase 4：RAG 增强与多模态（Day 7-9，预计 8-10 小时）— 加分项

**目标：提升推荐质量和多模态能力，拉开差距**

- [ ] **4.1** 混合检索策略
  - 关键词检索（BM25/MySQL LIKE） + 向量检索（FAISS）融合
  - 加入 Reranker 二次排序（已从 AgentCraft 复用）
  - 查询改写：用户口语化描述 → 结构化查询

- [ ] **4.2** 多模态能力增强
  - 用户上传图片 → CLIP/多模态模型提取视觉特征 → 向量空间检索相似商品
  - 商品图文联合 Embedding（文本 + 图片特征拼接）

- [ ] **4.3** 对话记忆与个性化
  - 会话内记忆：记住用户说过的偏好（「我是油皮」「预算 200 以内」）
  - 跨会话偏好画像：记录用户历史交互，下次推荐更精准
  - 复用 `memory_agent.py` + `tools/memory_read.py` + `tools/memory_write.py`

- [ ] **4.4** 推荐策略优化
  - 多样性：推荐结果不全是同品类
  - 对比能力：用户问「A 和 B 哪个好」→ 结构化对比表
  - 追问引导：「你是自用还是送人？」「更看重性价比还是品牌？」

---

### Phase 5：工程质量与体验优化（Day 9-10，预计 5-6 小时）— 加分项

- [ ] **5.1** Android 体验优化
  - 消息发送/接收动画
  - 骨架屏 Loading
  - 网络异常重试 + 离线提示
  - 深色模式适配
  - 多分辨率适配

- [ ] **5.2** 后端性能
  - Redis 缓存热门商品查询
  - LLM 响应流式传输优化
  - 数据库查询索引优化

- [ ] **5.3** 日志与可观测性
  - Agent 推理过程记录（thinking → tool_call → response）
  - 推荐效果日志（写入 recommendation_log 表）

---

### Phase 6：演示准备（Day 10-11，预计 4-5 小时）

- [ ] **6.1** 数据集优化
  - 100 个商品偏少，考虑用 LLM 扩充到 300-500 个
  - 补充商品评价、FAQ 等 RAG 语料

- [ ] **6.2** 演示场景脚本
  - 准备 5 个典型对话场景，确保演示时流畅：
    1. 「我想买一款保湿面霜，预算 200 左右」→ 精准推荐
    2. 发一张商品图片 → 找到相似商品
    3. 「帮我对比这两款手机」→ 结构化对比
    4. 「换一批推荐」→ 多样性推荐
    5. 「送女朋友生日礼物，她是干皮」→ 个性化 + 场景理解

- [ ] **6.3** APK 打包
  - Release 签名打包
  - 放到 `demo/apk/` 目录

- [ ] **6.4** 文档撰写
  - `docs/架构设计文档.md` — 系统架构图 + 技术选型说明
  - `docs/数据说明文档.md` — 数据集结构、入库流程
  - `docs/用户使用手册.md` — App 安装使用说明
  - `README.md` — 项目总览 + 快速开始

- [ ] **6.5** 截图与录屏
  - 放到 `demo/screenshots/`
  - 关键页面截图 + 一段 2-3 分钟演示视频

---

## 四、优先级与时间线总览

```
Week 1 (Day 1-7)：跑通最小闭环
  Day 1       → Phase 0：环境 + 数据入库
  Day 2-3     → Phase 1：Agent 电商适配
  Day 3-4     → Phase 2：Java 后端 API
  Day 4-7     → Phase 3：Android 客户端核心功能
                ✅ 里程碑：端到端对话 + 商品推荐可演示

Week 2 (Day 8-11)：增强与打磨
  Day 7-9     → Phase 4：RAG 增强 + 多模态
  Day 9-10    → Phase 5：工程体验优化
  Day 10-11   → Phase 6：演示准备
                ✅ 里程碑：比赛提交物完成
```

---

## 五、技术复用清单（来自 AgentCraft）

| 复用组件 | 来源路径 | 复用方式 |
|----------|----------|----------|
| FAISS 向量检索 | core/vector_store.py | 直接复用，新增商品索引 |
| 文本分割 | core/text_splitter.py | 直接复用 |
| Reranker 重排序 | core/reranker.py | 直接复用 |
| LLM 调用封装 | core/llm.py | 直接复用 |
| Agent 编排框架 | agent/orchestrator.py | 复用架构，新增电商 Workflow |
| 工具注册机制 | tools/registry.py | 复用，注册新工具 |
| 对话状态管理 | agent/state.py | 复用，扩展电商字段 |
| 记忆系统 | agent/memory_agent.py | 直接复用 |
| 对话 API | api/routes.py | 复用，调整响应格式 |
| 用户认证 | src/.../AuthServiceImpl.java | 直接复用 |
| Spring Boot 框架 | src/.../ | 直接复用，新增商品 Controller |

**需要新写的核心代码：**
- `data/import_products.py` — 数据导入
- `tools/product_*.py` — 4 个电商工具
- `workflows/shopping_agent.py` — 导购 Agent
- Android 端全部 UI 代码（约 15-20 个 Composable）
- Android 网络层 + ViewModel
- 新增 SQL 表 + Java Entity/Mapper/Service/Controller

---

## 六、风险与应对

| 风险 | 应对方案 |
|------|----------|
| LLM API 调用慢/不稳定 | 预设兜底回复 + 请求超时重试 + 缓存热门问答 |
| 100 个商品数据量太少 | Phase 6 用 LLM 批量生成扩充，或爬取公开数据集 |
| Android 开发经验不足 | 先用最简单的 Compose 实现，不追求花哨动画 |
| 多模态图片理解效果差 | 降级为图片 OCR 提取文字 → 文本检索，不依赖视觉模型 |
| 端到端调试困难 | Phase 1 先 curl 测试 Agent，Phase 2 再连 Android |
