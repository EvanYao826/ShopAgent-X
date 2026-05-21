# ShopAgent-X — RAG 多模态电商导购 AI Agent

<p align="center">
  <strong>基于 RAG + 多 Agent 编排的智能电商购物导购系统</strong>
</p>

---

## 📖 项目简介

ShopAgent-X 是一个完整的**多模态电商导购 AI Agent**系统，采用 **RAG（检索增强生成）+ 多 Agent 编排**架构，为电商场景提供智能化的购物问答、商品推荐、知识库管理等能力。

系统由四个核心模块组成：

| 模块 | 技术栈 | 作用 |
|------|--------|------|
| **Backend** | Spring Boot 3.2 + MyBatis Plus + Redis | Java 后端服务，提供 REST API、用户管理、商品管理、对话管理、缓存 |
| **Python AI Service** | FastAPI + LangChain + DashScope | AI 核心服务，负责 RAG 检索、Agent 编排、文档解析、向量存储 |
| **Frontend** | React 18 + Vite + ECharts | Web 前端，提供用户聊天界面和管理后台 |
| **Android App** | Kotlin + Jetpack Compose | Android 移动端客户端（基础框架） |

### 核心特性

- **🤖 多 Agent 智能路由**：RouterAgent 自动识别用户意图（购物导购/商品搜索/商品对比/知识问答/闲聊/售后），将请求分发给专业子 Agent
- **📚 RAG 知识检索**：基于向量数据库（Milvus/FAISS）的文档检索，支持语义切片、Rerank 重排序、引用溯源
- **🛒 电商导购能力**：商品搜索、商品对比、个性化推荐、商品卡片展示、SKU 规格展示
- **💬 多轮对话上下文**：对话摘要、用户偏好记忆、上下文窗口裁剪
- **🔍 多模态输入**：支持文本 + 图片输入，OCR 文字提取
- **📊 Agent 可观测性**：完整的 Agent 运行记录、步骤追踪、工具调用日志、统计看板
- **⚡ 多级缓存**：Caffeine 本地缓存 + Redis 分布式缓存，提升响应速度
- **🔐 安全认证**：JWT Token 认证、管理员/普通用户角色隔离、敏感词过滤

### 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      客户端层                                │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│   │  Web 前端     │  │ Android App  │  │  其他客户端    │      │
│   │  (React)     │  │  (Kotlin)    │  │              │      │
│   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
└──────────┼─────────────────┼─────────────────┼──────────────┘
           │                 │                 │
           ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────────┐
│                   Java Backend (Port 8080)                   │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐   │
│  │ Auth API  │ │ Chat API  │ │ Product   │ │ Admin API │   │
│  │ (JWT)     │ │           │ │ API       │ │           │   │
│  └─────┬─────┘ └─────┬─────┘ └───────────┘ └───────────┘   │
│        │             │                                      │
│  ┌─────┴─────────────┴──────────┐                           │
│  │   AiService (REST 调用 Python) │                         │
│  └──────────────┬───────────────┘                           │
│        ┌────────┴────────┐                                  │
│        │  MySQL + Redis  │                                  │
│        └─────────────────┘                                  │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP REST
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                Python AI Service (Port 8000)                 │
│  ┌─────────────┐                                            │
│  │ RouterAgent │ ◄── 意图分类 (shopping/chitchat/admin...)  │
│  └──────┬──────┘                                            │
│         │ 分发                                              │
│  ┌──────┴──────────────────────────────────────────┐        │
│  │  ShoppingAgent │ KnowledgeQA │ Chitchat │ Admin │        │
│  └──────┬──────────────────────────────────────────┘        │
│         │                                                   │
│  ┌──────┴──────┐  ┌───────────┐  ┌──────────────────┐      │
│  │  Orchestrator│  │  Planner  │  │    Executor      │      │
│  │  (编排器)    │  │  (规划器) │  │    (执行器)      │      │
│  └─────────────┘  └───────────┘  └──────────────────┘      │
│                                                             │
│  ┌──────────────────────────────────────────────────┐       │
│  │  Tools: knowledge_search, rerank, question_rewrite│      │
│  │         memory_read/write, ocr_extract, citation  │      │
│  └──────────────────────────────────────────────────┘       │
│                                                             │
│  ┌─────────────┐  ┌───────────┐  ┌───────────┐             │
│  │ Vector Store │  │  MySQL    │  │  Redis    │             │
│  │ (FAISS/Milvus)│ └───────────┘  └───────────┘             │
│  └─────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 项目结构

```
ShopAgent-X-main/
├── backend/                          # Java 后端 + Python AI 服务 + 前端
│   ├── pom.xml                       # Maven 依赖配置
│   ├── src/main/java/com/demo/aiknowledge/
│   │   ├── AiKnowledgeSystemApplication.java  # Spring Boot 启动类
│   │   ├── common/                   # 公共工具类
│   │   │   ├── Result.java           # 统一返回结果封装
│   │   │   ├── JwtUtil.java          # JWT 工具类
│   │   │   ├── ErrorCode.java        # 错误码定义
│   │   │   └── SensitiveWordUtil.java # 敏感词过滤
│   │   ├── config/                   # 配置类
│   │   │   ├── SecurityConfig.java   # Spring Security 配置
│   │   │   ├── WebConfig.java        # Web/CORS 配置
│   │   │   ├── CacheConfig.java      # 多级缓存配置
│   │   │   ├── JwtFilter.java        # JWT 过滤器
│   │   │   └── RestTemplateConfig.java
│   │   ├── controller/               # REST 控制器
│   │   │   ├── AuthController.java   # 认证（登录/注册）
│   │   │   ├── ChatController.java   # 对话管理
│   │   │   ├── AgentController.java  # Agent 运行管理
│   │   │   ├── ProductController.java# 商品管理
│   │   │   ├── CategoryController.java# 品类管理
│   │   │   ├── KnowledgeController.java# 知识库管理
│   │   │   ├── RecommendController.java# 推荐接口
│   │   │   ├── NoticeController.java # 公告管理
│   │   │   ├── CacheController.java  # 缓存管理
│   │   │   └── admin/                # 管理后台控制器
│   │   │       ├── AdminController.java
│   │   │       ├── DashboardController.java
│   │   │       ├── AgentAdminController.java
│   │   │       └── NoticeController.java
│   │   ├── entity/                   # 数据实体 (MyBatis Plus)
│   │   │   ├── User.java             # 用户
│   │   │   ├── Admin.java            # 管理员
│   │   │   ├── Product.java          # 商品
│   │   │   ├── ProductSku.java       # 商品 SKU
│   │   │   ├── ProductImage.java     # 商品图片
│   │   │   ├── ProductReview.java    # 商品评价
│   │   │   ├── ProductFaq.java       # 商品 FAQ
│   │   │   ├── Category.java         # 品类
│   │   │   ├── Conversation.java     # 会话
│   │   │   ├── Message.java          # 消息
│   │   │   ├── ConversationContext.java # 对话上下文
│   │   │   ├── AgentRun.java         # Agent 运行记录
│   │   │   ├── AgentStep.java        # Agent 执行步骤
│   │   │   ├── ToolCall.java         # 工具调用记录
│   │   │   ├── KnowledgeDoc.java     # 知识库文档
│   │   │   ├── KnowledgeChunk.java   # 文档切片
│   │   │   ├── QaLog.java            # 问答日志
│   │   │   ├── QaUnanswered.java     # 未命中问题
│   │   │   ├── RecommendationLog.java# 推荐日志
│   │   │   ├── UserFavorite.java     # 用户收藏
│   │   │   ├── UserBrowseHistory.java# 浏览历史
│   │   │   └── Notice.java           # 公告
│   │   ├── dto/                      # 数据传输对象
│   │   ├── mapper/                   # MyBatis Plus Mapper 接口
│   │   ├── service/                  # 业务服务接口
│   │   │   └── impl/                 # 服务实现
│   │   ├── interceptor/              # 拦截器
│   │   │   └── AdminInterceptor.java # 管理员权限拦截
│   │   ├── exception/                # 异常处理
│   │   │   ├── BusinessException.java
│   │   │   └── GlobalExceptionHandler.java
│   │   └── utils/
│   │       └── CacheUtils.java
│   ├── src/main/resources/
│   │   ├── application.yml           # 应用配置
│   │   └── mapper/                   # MyBatis XML 映射文件
│   ├── sql/
│   │   └── init.sql                  # 数据库初始化脚本（含全量表结构 + 示例数据）
│   │
│   ├── python-service/               # Python AI 核心服务
│   │   ├── main.py                   # FastAPI 服务入口
│   │   ├── requirements.txt          # Python 依赖
│   │   ├── .env.example              # 环境变量示例
│   │   ├── api/                      # API 路由
│   │   │   ├── routes.py             # RAG 问答、文档解析等路由
│   │   │   └── agent_routes.py       # Agent 运行、流式输出等路由
│   │   ├── agent/                    # Agent 核心框架
│   │   │   ├── orchestrator.py       # Agent 编排器（核心）
│   │   │   ├── planner.py            # 步骤规划器
│   │   │   ├── executor.py           # 步骤执行器
│   │   │   ├── state.py              # Agent 状态管理
│   │   │   ├── events.py             # 事件总线
│   │   │   ├── policies.py           # 执行策略（重试、超时、输入校验）
│   │   │   └── memory_agent.py       # 记忆管理 Agent
│   │   ├── workflows/                # Agent 工作流（子 Agent）
│   │   │   ├── router_agent.py       # 路由 Agent（意图识别 + 分发）
│   │   │   ├── shopping_agent.py     # 购物导购 Agent
│   │   │   ├── knowledge_qa_agent.py # 知识问答 Agent
│   │   │   ├── chitchat_agent.py     # 闲聊 Agent
│   │   │   ├── reasoning_agent.py    # 推理 Agent
│   │   │   ├── retrieval_agent.py    # 检索 Agent
│   │   │   ├── ops_agent.py          # 运维 Agent
│   │   │   ├── inspection_agent.py   # 知识巡检 Agent
│   │   │   ├── admin_copilot_agent.py# 管理员 Copilot Agent
│   │   │   └── base_agent.py         # Agent 基类
│   │   ├── tools/                    # Agent 可用工具
│   │   │   ├── knowledge_search.py   # 知识库检索
│   │   │   ├── rerank.py             # 检索结果重排序
│   │   │   ├── question_rewrite.py   # 问题改写
│   │   │   ├── memory_read.py        # 记忆读取
│   │   │   ├── memory_write.py       # 记忆写入
│   │   │   ├── ocr_extract.py        # OCR 文字提取
│   │   │   ├── doc_summary.py        # 文档摘要
│   │   │   ├── citation.py           # 引用生成
│   │   │   ├── execution.py          # 通用执行器
│   │   │   ├── registry.py           # 工具注册中心
│   │   │   └── base.py               # 工具基类
│   │   ├── core/                     # 核心组件
│   │   │   ├── config.py             # 配置管理
│   │   │   ├── llm.py                # LLM 调用封装
│   │   │   ├── vector_store.py       # 向量存储（FAISS/Milvus）
│   │   │   ├── text_splitter.py      # 文本切分器
│   │   │   ├── reranker.py           # 重排序器
│   │   │   ├── parser.py             # 文档解析器
│   │   │   ├── mysql_client.py       # MySQL 客户端
│   │   │   └── redis_client.py       # Redis 客户端
│   │   ├── intent/                   # 意图分类
│   │   │   └── classifier.py
│   │   └── tests/                    # 测试文件
│   │
│   └── frontend/                     # React 前端
│       ├── package.json              # npm 依赖
│       ├── vite.config.js            # Vite 配置（含代理）
│       ├── index.html
│       └── src/
│           ├── App.jsx               # 根组件 + 路由
│           ├── main.jsx              # 入口
│           ├── api/                  # API 封装
│           │   ├── index.js          # Axios 实例
│           │   ├── admin.js          # 管理后台 API
│           │   ├── dashboard.js      # 看板 API
│           │   └── notice.js         # 公告 API
│           ├── components/
│           │   └── Header.jsx        # 顶部导航
│           └── pages/
│               ├── Chat.jsx          # 用户聊天页
│               ├── Login.jsx         # 登录页
│               ├── Register.jsx      # 注册页
│               ├── Knowledge.jsx     # 知识库页
│               └── admin/            # 管理后台页面
│                   ├── AdminDashboard.jsx  # 管理看板
│                   ├── AdminChat.jsx       # 管理员 AI 对话
│                   ├── Dashboard.jsx       # 数据统计
│                   ├── UserManagement.jsx  # 用户管理
│                   ├── KnowledgeManagement.jsx # 知识库管理
│                   ├── KnowledgeInspection.jsx # 知识巡检
│                   ├── QaLogManagement.jsx # 问答日志
│                   ├── AgentRunManagement.jsx # Agent 运行监控
│                   ├── NoticeManagement.jsx # 公告管理
│                   └── Reports.jsx         # 报表
│
├── android/                          # Android 客户端
│   ├── build.gradle.kts              # 项目级 Gradle 配置
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts          # App 级 Gradle 配置
│       └── src/main/
│           ├── AndroidManifest.xml
│           └── java/com/evanyao/shopagent/
│               ├── MainActivity.kt   # 主 Activity
│               └── ui/theme/         # Compose 主题
│
├── data/                             # 电商数据集
│   └── ecommerce_agent_dataset/
│       ├── 1_美妆护肤/               # 美妆护肤品类
│       │   ├── data/                 # 商品 JSON 数据 (25个商品)
│       │   └── images/               # 商品实拍图片
│       ├── 2_数码电子/               # 数码电子品类
│       ├── 3_服饰运动/               # 服饰运动品类
│       └── 4_食品生活/               # 食品生活品类
│
└── docker-compose.yml                # Docker 编排（MySQL + Redis）
```

---

## 🛠 技术栈详情

### Java Backend

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.3 | 应用框架 |
| MyBatis Plus | 3.5.5 | ORM 框架 |
| Spring Security | - | 安全认证 |
| JWT (jjwt) | 0.11.5 | Token 认证 |
| Spring Data Redis | - | Redis 缓存 |
| Caffeine | 3.1.8 | 本地缓存 |
| Hutool | 5.8.25 | Java 工具库 |
| Lombok | - | 代码简化 |
| SpringDoc OpenAPI | 2.3.0 | API 文档 (Swagger) |
| 阿里云 SMS SDK | 2.2.1 | 短信验证码 |
| 七牛云 SDK | 7.13.0 | 文件存储 |
| MySQL Connector | - | 数据库驱动 |

### Python AI Service

| 技术 | 版本 | 用途 |
|------|------|------|
| FastAPI | ≥0.110.0 | Web 框架 |
| LangChain | ≥0.1.11 | LLM 应用框架 |
| DashScope | ≥1.14.0 | 阿里云 AI API（Embedding） |
| FAISS | ≥1.8.0 | 本地向量数据库 |
| Milvus | ≥2.4.0 (可选) | 生产级向量数据库 |
| sentence-transformers | ≥2.5.1 | 本地 Embedding 模型 |
| PyTesseract + Pillow | - | OCR 文字识别 |
| Cohere | ≥4.0.0 (可选) | Rerank 重排序 |
| mysql-connector-python | ≥8.2.0 | MySQL 客户端 |
| Redis | ≥5.0.0 | Redis 客户端 |

### Frontend

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 18.2.0 | UI 框架 |
| Vite | 5.1.0 | 构建工具 |
| React Router DOM | 6.22.0 | 路由管理 |
| Axios | 1.6.7 | HTTP 客户端 |
| ECharts | 6.0.0 | 数据可视化 |

### Android

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | - | 开发语言 |
| Jetpack Compose | - | 声明式 UI |
| Material3 | - | Material Design 3 |
| Gradle (Kotlin DSL) | 9.4.1 | 构建工具 |
| minSdk | 24 | 最低 Android 版本 |
| targetSdk | 36 | 目标 Android 版本 |

---

## 🚀 快速开始

### 环境要求

- **JDK 17**+
- **Python 3.10**+
- **Node.js 18**+
- **MySQL 8.0**+
- **Redis 7**+
- **Maven 3.8**+（或使用 IDE 内置）
- **Android Studio**（可选，编译 Android 客户端）

### 1. 启动基础服务（MySQL + Redis）

使用 Docker Compose 一键启动：

```bash
cd ShopAgent-X-main
docker-compose up -d
```

这将启动：
- MySQL 8.0（端口 3306，root 密码：123456，数据库：ai_knowledge_db）
- Redis 7（端口 6379）

数据库会自动执行 `backend/sql/init.sql` 初始化脚本，创建所有表结构和默认管理员账号。

> **默认管理员账号**：`admin` / `admin123`

### 2. 启动 Python AI 服务

```bash
cd backend/python-service

# 创建虚拟环境
python -m venv venv

# 激活虚拟环境
# Windows:
venv\Scripts\activate
# Linux/macOS:
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env
# 编辑 .env 文件，填入你的 DASHSCOPE_API_KEY
# 获取地址：https://dashscope.console.aliyun.com/

# 启动服务（默认端口 8000）
python main.py
```

服务启动后访问：
- API 文档：http://localhost:8000/docs
- 健康检查：http://localhost:8000/health

### 3. 启动 Java Backend

```bash
cd backend

# 使用 Maven 构建并运行
mvn spring-boot:run

# 或者先打包再运行
mvn clean package -DskipTests
java -jar target/shop-agent-x-0.0.1-SNAPSHOT.jar
```

服务默认运行在 **端口 8080**。

> **注意**：Java Backend 会通过 REST 调用 Python AI 服务（默认地址 `http://127.0.0.1:8000/api`），请确保 Python 服务已启动。

### 4. 启动 Frontend

```bash
cd backend/frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端默认运行在 **http://localhost:5173**，API 请求会自动代理到 Java 后端 `http://localhost:8080`。

### 5. 编译 Android 客户端（可选）

使用 Android Studio 打开 `android/` 目录，等待 Gradle 同步完成后即可编译运行。

---

## ⚙️ 配置说明

### Java Backend 配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
server:
  port: 8080                        # 服务端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/shop_agent_db
    username: root
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379

ai:
  service:
    url: http://127.0.0.1:8000/api  # Python AI 服务地址

jwt:
  secret: your-secret-key           # 生产环境务必修改
  expiration: 3600000               # Token 过期时间 (ms)
```

支持通过环境变量覆盖所有配置项（如 `SERVER_PORT`、`DB_HOST`、`AI_SERVICE_URL` 等）。

### Python AI 服务配置

编辑 `backend/python-service/.env`：

```env
# 必填：阿里云 DashScope API Key
DASHSCOPE_API_KEY=sk-xxxxxxxx

# 向量数据库选择（false=本地FAISS, true=Milvus）
USE_MILVUS=false

# Rerank 策略（simple/bge/cohere/none）
RERANKER_TYPE=simple

# 文本切分策略
CHUNK_STRATEGY=semantic
CHUNK_SIZE=500
CHUNK_OVERLAP=50
```

---

## 📡 API 接口

### 用户端 API（/api）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/chat/conversations` | 获取会话列表 |
| POST | `/api/chat/conversations` | 创建新会话 |
| POST | `/api/chat/messages` | 发送消息 |
| GET | `/api/chat/messages` | 获取消息列表 |
| POST | `/api/chat/upload/image` | 上传图片 |
| POST | `/api/chat/messages/feedback` | 消息反馈（点赞/踩） |
| GET | `/api/products` | 商品列表 |
| GET | `/api/products/{id}` | 商品详情 |
| GET | `/api/categories` | 品类列表 |
| POST | `/api/recommend` | 获取推荐 |

### Agent API（/api/agent）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/run` | 执行 Agent（同步） |
| POST | `/api/agent/run/stream` | 执行 Agent（SSE 流式） |
| GET | `/api/agent/run/{runId}` | 获取运行状态 |
| GET | `/api/agent/run/{runId}/steps` | 获取执行步骤 |
| GET | `/api/agent/tools` | 列出可用工具 |
| GET | `/api/agent/stats` | 获取统计信息 |

### 管理后台 API（/api/admin）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/login` | 管理员登录 |
| GET | `/api/admin/dashboard/stats` | 数据统计看板 |
| GET | `/api/admin/users` | 用户管理 |
| GET | `/api/admin/knowledge` | 知识库管理 |
| POST | `/api/admin/knowledge/upload` | 上传知识文档 |
| GET | `/api/admin/agent/runs` | Agent 运行监控 |
| GET | `/api/admin/qa-logs` | 问答日志 |
| POST | `/api/admin/chat` | 管理员 AI 对话 |

---

## 🤖 Agent 工作流

### 意图分类

系统通过 `RouterAgent` 自动识别用户意图，支持以下任务类型：

| 任务类型 | 说明 | 子 Agent |
|----------|------|----------|
| `shopping` | 购物导购 | ShoppingAgent |
| `product_search` | 商品搜索 | ShoppingAgent |
| `product_compare` | 商品对比 | ShoppingAgent |
| `knowledge_qa` | 知识问答 | KnowledgeQAAgent |
| `chitchat` | 闲聊 | ChitchatAgent |
| `admin_copilot` | 管理员助手 | AdminCopilotAgent |

### Agent 执行流程

```
用户输入 → RouterAgent（意图分类）
              │
              ├── shopping → ShoppingAgent
              │     ├── intent_recognition（意图识别）
              │     ├── question_rewrite（问题改写）
              │     ├── knowledge_search（知识检索）
              │     ├── result_evaluation（结果评估）
              │     └── answer_generation（生成回答 + 商品卡片）
              │
              ├── knowledge_qa → KnowledgeQAAgent
              │     ├── intent_recognition
              │     ├── clarification（澄清判断）
              │     ├── question_rewrite
              │     ├── knowledge_search
              │     └── answer_generation
              │
              ├── chitchat → ChitchatAgent
              │     └── identity_answer（身份回答）
              │
              └── admin_copilot → AdminCopilotAgent
                    └── admin_operation（管理操作）
```

### 工具列表

| 工具 | 说明 |
|------|------|
| `knowledge_search` | 知识库语义检索（FAISS/Milvus） |
| `rerank` | 检索结果重排序（BGE/Cohere/Simple） |
| `question_rewrite` | 问题改写（扩展同义词、纠正错别字） |
| `memory_read` | 读取用户历史记忆和偏好 |
| `memory_write` | 写入对话记忆 |
| `ocr_extract` | 图片 OCR 文字提取 |
| `doc_summary` | 文档摘要生成 |
| `citation` | 引用来源生成 |

---

## 📊 数据库设计

系统包含以下核心数据表：

### 用户体系
- `user` — 普通用户表（支持偏好标签、肤质等画像字段）
- `admin` — 管理员表

### 电商商品体系
- `category` — 商品品类表（美妆护肤/数码电子/服饰运动/食品生活）
- `product` — 商品主表（含向量化状态标记）
- `product_sku` — 商品 SKU 规格表
- `product_image` — 商品图片表
- `product_review` — 商品评价表（同时作为 RAG 语料）
- `product_faq` — 商品 FAQ 表（同时作为 RAG 语料）

### 对话体系
- `conversation` — 会话表
- `message` — 消息表（支持文本/商品卡片/图片等多种类型）
- `conversation_context` — 对话上下文表（摘要、用户偏好、记忆）

### Agent 运行体系
- `agent_run` — Agent 运行记录表
- `agent_step` — Agent 执行步骤表
- `tool_call` — 工具调用记录表

### 推荐与行为体系
- `recommendation_log` — 推荐日志表（含用户反馈）
- `user_browse_history` — 用户浏览记录
- `user_favorite` — 用户收藏表

### 知识库体系
- `knowledge_doc` — 知识库文档表
- `knowledge_chunk` — 文档切片表
- `qa_log` — 问答日志表
- `qa_unanswered` — 未命中问题表

---

## 📦 电商数据集

项目内置了完整的电商模拟数据集，位于 `data/ecommerce_agent_dataset/`，包含 4 个品类、每个品类 25 个商品：

| 品类 | 商品编码前缀 | 内容 |
|------|-------------|------|
| 美妆护肤 | `p_beauty_` | 精华、面霜、口红等，含营销描述、FAQ、用户评价、实拍图 |
| 数码电子 | `p_digital_` | 手机、耳机、笔记本等 |
| 服饰运动 | `p_clothes_` | 运动鞋、运动服等 |
| 食品生活 | `p_food_` | 零食、饮料等 |

每个商品 JSON 包含：
- 基本信息（标题、品牌、价格、SKU 规格）
- RAG 知识（营销描述、官方 FAQ、用户评价）
- 商品图片路径

---

## 🔧 开发指南

### 添加新的 Agent 工作流

1. 在 `backend/python-service/workflows/` 下创建新的 Agent 类，继承 `BaseAgent`
2. 实现 `execute` 方法
3. 在 `RouterAgent` 中注册新的路由规则

### 添加新的 Agent 工具

1. 在 `backend/python-service/tools/` 下创建新的工具类
2. 继承 `BaseTool`，实现 `execute` 方法
3. 使用 `@tool_registry.register` 装饰器注册

### 前端开发

```bash
cd backend/frontend
npm run dev    # 开发模式（热更新）
npm run build  # 生产构建
npm run preview # 预览构建结果
```

---

## 🐳 Docker 部署

目前提供 MySQL + Redis 的 Docker Compose 编排：

```bash
docker-compose up -d        # 启动
docker-compose down          # 停止
docker-compose logs -f       # 查看日志
```

---

## 📄 License

本项目仅供学习和研究使用。

---

## 致谢

- [Spring Boot](https://spring.io/projects/spring-boot)
- [LangChain](https://github.com/langchain-ai/langchain)
- [FastAPI](https://fastapi.tiangolo.com/)
- [DashScope](https://dashscope.aliyun.com/)
- [FAISS](https://github.com/facebookresearch/faiss)
- [React](https://react.dev/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
