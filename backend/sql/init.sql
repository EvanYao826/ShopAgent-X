-- ============================================================
-- ShopAgent-X 电商导购 AI Agent 数据库初始化脚本
-- 基于 MySQL 8.0，字符集 utf8mb4
-- 包含：用户体系 + 对话体系 + Agent 运行体系 + 电商商品体系
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS shop_agent_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE shop_agent_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 一、用户体系
-- ============================================================

-- ------------------------------------------------------------
-- 1.1 用户表
-- 存储普通用户（App 端消费者）的基本信息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID，主键自增',
  `username`    VARCHAR(50)  DEFAULT NULL            COMMENT '用户名/昵称',
  `phone`       VARCHAR(20)  NOT NULL                COMMENT '手机号，用于登录',
  `password`    VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 加密存储）',
  `avatar_url`  VARCHAR(500) DEFAULT NULL            COMMENT '用户头像 URL',
  `gender`      TINYINT      DEFAULT 0               COMMENT '性别：0-未知 1-男 2-女',
  `age_range`   VARCHAR(20)  DEFAULT NULL            COMMENT '年龄段：18-24/25-30/31-40/40+',
  `skin_type`   VARCHAR(30)  DEFAULT NULL            COMMENT '肤质（美妆场景）：干性/油性/混合/敏感/中性',
  `preference_tags` JSON     DEFAULT NULL            COMMENT '用户偏好标签 JSON 数组，如["平价","大牌","敏感肌"]',
  `status`      INT          DEFAULT 1               COMMENT '账号状态：1-正常 0-封禁',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表——存储 App 端消费者信息';

-- ------------------------------------------------------------
-- 1.2 管理员表
-- 存储后台管理人员信息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `admin`;
CREATE TABLE `admin` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '管理员ID，主键自增',
  `username`    VARCHAR(50)  NOT NULL                COMMENT '管理员用户名',
  `password`    VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 加密存储）',
  `role`        VARCHAR(20)  DEFAULT 'admin'         COMMENT '角色：admin-普通管理员 super_admin-超级管理员',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表——存储后台管理员账号';

-- 默认管理员账号 admin / admin123
INSERT INTO `admin` (`username`, `password`, `role`) VALUES ('admin', 'admin123', 'admin');


-- ============================================================
-- 二、电商商品体系
-- ============================================================

-- ------------------------------------------------------------
-- 2.1 商品品类表
-- 存储一级品类（美妆护肤、数码电子、服饰运动、食品生活）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '品类ID，主键自增',
  `name`        VARCHAR(50)  NOT NULL                COMMENT '品类名称，如：美妆护肤、数码电子',
  `description` VARCHAR(500) DEFAULT NULL            COMMENT '品类简介',
  `icon_url`    VARCHAR(500) DEFAULT NULL            COMMENT '品类图标 URL',
  `sort_order`  INT          DEFAULT 0               COMMENT '排序权重，数值越小越靠前',
  `is_active`   TINYINT(1)   DEFAULT 1               COMMENT '是否启用：1-启用 0-停用',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品品类表——存储一级商品分类';

-- 初始化 4 个品类
INSERT INTO `category` (`name`, `description`, `sort_order`) VALUES
  ('美妆护肤', '面部护肤、彩妆、香水等美妆产品', 1),
  ('数码电子', '手机、电脑、耳机等数码产品', 2),
  ('服饰运动', '服装、鞋靴、运动装备等', 3),
  ('食品生活', '零食、饮料、生鲜等食品及生活用品', 4);

-- ------------------------------------------------------------
-- 2.2 商品主表
-- 存储商品核心信息，一个商品可有多个 SKU（规格）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '商品ID，主键自增',
  `product_code`    VARCHAR(50)   NOT NULL                 COMMENT '商品编码（外部ID），如 p_beauty_001',
  `category_id`     BIGINT        NOT NULL                 COMMENT '所属品类ID，关联 category.id',
  `title`           VARCHAR(500)  NOT NULL                 COMMENT '商品标题/名称',
  `brand`           VARCHAR(100)  DEFAULT NULL             COMMENT '品牌名称',
  `sub_category`    VARCHAR(50)   DEFAULT NULL             COMMENT '子分类，如：精华、面霜、手机、笔记本',
  `base_price`      DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '基础价格（最低 SKU 价格）',
  `image_url`       VARCHAR(500)  DEFAULT NULL             COMMENT '商品主图 URL',
  `description`     TEXT          DEFAULT NULL             COMMENT '商品营销描述（RAG 语料）',
  `tags`            VARCHAR(1000) DEFAULT NULL             COMMENT '商品标签，逗号分隔，如：抗老,保湿,精华',
  `rating`          DECIMAL(3,2)  DEFAULT 0.00             COMMENT '综合评分（1.00-5.00），由评价计算得出',
  `review_count`    INT           DEFAULT 0                COMMENT '评价总数',
  `sales_count`     INT           DEFAULT 0                COMMENT '累计销量',
  `status`          TINYINT       DEFAULT 1                COMMENT '商品状态：1-上架 0-下架',
  `embedding_status`TINYINT       DEFAULT 0                COMMENT '向量化状态：0-未处理 1-已生成向量',
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_brand` (`brand`),
  KEY `idx_sub_category` (`sub_category`),
  KEY `idx_status` (`status`),
  KEY `idx_embedding_status` (`embedding_status`),
  KEY `idx_base_price` (`base_price`),
  KEY `idx_rating` (`rating` DESC),
  KEY `idx_sales_count` (`sales_count` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品主表——存储商品核心信息';

-- ------------------------------------------------------------
-- 2.3 商品 SKU 表
-- 存储商品的具体规格（如 30ml/50ml、黑色/白色）
-- 一个 product 对应多个 sku
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product_sku`;
CREATE TABLE `product_sku` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT  COMMENT 'SKU ID，主键自增',
  `product_id`  BIGINT        NOT NULL                 COMMENT '所属商品ID，关联 product.id',
  `sku_code`    VARCHAR(50)   NOT NULL                 COMMENT 'SKU 编码，如 s_p_beauty_001_1',
  `properties`  JSON          DEFAULT NULL             COMMENT '规格属性 JSON，如 {"容量":"30ml","颜色":"黑色"}',
  `price`       DECIMAL(10,2) NOT NULL                 COMMENT '该规格的售价',
  `stock`       INT           DEFAULT 999              COMMENT '库存数量',
  `is_default`  TINYINT(1)    DEFAULT 0                COMMENT '是否默认选中：1-是 0-否',
  `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表——存储商品的具体规格与价格';

-- ------------------------------------------------------------
-- 2.4 商品图片表
-- 存储商品的多张图片（主图、详情图、场景图等）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product_image`;
CREATE TABLE `product_image` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '图片ID，主键自增',
  `product_id`  BIGINT       NOT NULL                 COMMENT '所属商品ID，关联 product.id',
  `image_url`   VARCHAR(500) NOT NULL                 COMMENT '图片 URL 或本地路径',
  `image_type`  VARCHAR(20)  DEFAULT 'detail'         COMMENT '图片类型：main-主图 detail-详情图 scene-场景图',
  `sort_order`  INT          DEFAULT 0                COMMENT '排序权重',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品图片表——存储商品的多张图片';

-- ------------------------------------------------------------
-- 2.5 商品评价表
-- 存储用户对商品的评价，同时作为 RAG 语料
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product_review`;
CREATE TABLE `product_review` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '评价ID，主键自增',
  `product_id`  BIGINT       NOT NULL                 COMMENT '所评商品ID，关联 product.id',
  `user_id`     BIGINT       DEFAULT NULL             COMMENT '评价用户ID，关联 user.id（可为空，支持导入外部评价）',
  `nickname`    VARCHAR(50)  DEFAULT '匿名用户'        COMMENT '评价者昵称（外部导入时使用）',
  `rating`      TINYINT      NOT NULL                 COMMENT '评分：1-5 星',
  `content`     TEXT         NOT NULL                 COMMENT '评价内容，同时作为 RAG 语料被检索',
  `is_anonymous`TINYINT(1)   DEFAULT 0                COMMENT '是否匿名：1-匿名 0-实名',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_rating` (`rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表——存储用户评价，同时作为 RAG 检索语料';

-- ------------------------------------------------------------
-- 2.6 商品 FAQ 表
-- 存储商品的官方常见问题解答，作为 RAG 语料
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product_faq`;
CREATE TABLE `product_faq` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'FAQ ID，主键自增',
  `product_id`  BIGINT       NOT NULL                 COMMENT '所属商品ID，关联 product.id',
  `question`    VARCHAR(500) NOT NULL                 COMMENT '问题内容',
  `answer`      TEXT         NOT NULL                 COMMENT '官方解答',
  `sort_order`  INT          DEFAULT 0                COMMENT '排序权重',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品FAQ表——存储官方问答，作为 RAG 检索语料';


-- ============================================================
-- 三、对话体系
-- ============================================================

-- ------------------------------------------------------------
-- 3.1 会话表
-- 存储用户与 AI 导购的对话会话
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '会话ID，主键自增',
  `user_id`     BIGINT       NOT NULL                 COMMENT '发起会话的用户ID，关联 user.id',
  `title`       VARCHAR(255) DEFAULT '新对话'          COMMENT '会话标题（自动取首条消息摘要）',
  `scene`       VARCHAR(30)  DEFAULT 'shopping'       COMMENT '会话场景：shopping-导购 chitchat-闲聊 after_sale-售后',
  `is_pinned`   TINYINT(1)   DEFAULT 0                COMMENT '是否置顶：1-置顶 0-普通',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表——存储用户与 AI 的对话会话';

-- ------------------------------------------------------------
-- 3.2 消息表
-- 存储对话中的每条消息，支持文本/商品卡片/图片等多种类型
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '消息ID，主键自增',
  `conversation_id` BIGINT        NOT NULL                 COMMENT '所属会话ID，关联 conversation.id',
  `role`            VARCHAR(20)   NOT NULL                 COMMENT '消息角色：user-用户 assistant-AI',
  `content`         TEXT          NOT NULL                 COMMENT '消息文本内容',
  `message_type`    VARCHAR(20)   DEFAULT 'text'           COMMENT '消息类型：text-纯文本 product_card-商品卡片 image-图片 mixed-混合',
  `product_cards`   JSON          DEFAULT NULL             COMMENT 'AI 推荐的商品卡片 JSON 数组，结构：[{product_id, title, price, image_url, reason}]',
  `image_url`       VARCHAR(500)  DEFAULT NULL             COMMENT '用户发送的图片 URL（多模态输入）',
  `sources`         TEXT          DEFAULT NULL             COMMENT 'RAG 参考来源 JSON',
  `task_type`       VARCHAR(50)   DEFAULT 'unknown'        COMMENT 'Agent 任务类型：shopping/chitchat/product_search/product_compare/unknown',
  `importance_score`DOUBLE        DEFAULT 0.5              COMMENT '消息重要性评分（0-1），用于上下文窗口裁剪',
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
  `feedback_type`   VARCHAR(20)   DEFAULT NULL             COMMENT '用户反馈：like-满意 dislike-不满意',
  `feedback_time`   DATETIME      DEFAULT NULL             COMMENT '反馈时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_role` (`role`),
  KEY `idx_task_type` (`task_type`),
  KEY `idx_feedback_type` (`feedback_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表——存储对话中的每条消息';

-- ------------------------------------------------------------
-- 3.3 对话上下文表
-- 存储会话级的上下文摘要和记忆，用于多轮对话连贯性
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `conversation_context`;
CREATE TABLE `conversation_context` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT           COMMENT '主键ID',
  `conversation_id` BIGINT   NOT NULL                          COMMENT '关联会话ID，唯一',
  `user_id`         BIGINT   DEFAULT NULL                      COMMENT '用户ID',
  `summary`         TEXT     DEFAULT NULL                      COMMENT '对话摘要（长期记忆），如：用户在找平价保湿面霜，偏好敏感肌适用',
  `user_preferences`JSON     DEFAULT NULL                      COMMENT '本轮对话提取的用户偏好 JSON，如 {"budget":"200以内","skin_type":"敏感肌","brand":"偏好大牌"}',
  `mentioned_products` JSON  DEFAULT NULL                      COMMENT '本轮对话中提到的商品ID列表，用于去重推荐',
  `embedding`       TEXT     DEFAULT NULL                      COMMENT '对话向量（JSON 格式），用于相似对话检索',
  `window_size`     INT      DEFAULT 10                        COMMENT '上下文窗口大小（保留最近 N 轮消息）',
  `importance_score`DOUBLE   DEFAULT 0.5                       COMMENT '重要性评分（0-1）',
  `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `update_time`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_id` (`conversation_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话上下文表——存储会话级摘要与用户偏好';


-- ============================================================
-- 四、Agent 运行体系
-- ============================================================

-- ------------------------------------------------------------
-- 4.1 Agent 运行记录表
-- 记录每次 Agent 调度的全过程
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `agent_run`;
CREATE TABLE `agent_run` (
  `id`              VARCHAR(36)  NOT NULL                COMMENT '主键ID（UUID）',
  `run_id`          VARCHAR(36)  NOT NULL                COMMENT '运行唯一标识',
  `trace_id`        VARCHAR(64)  DEFAULT NULL            COMMENT '链路追踪ID',
  `conversation_id` VARCHAR(36)  DEFAULT NULL            COMMENT '关联的会话ID',
  `user_id`         VARCHAR(36)  DEFAULT NULL            COMMENT '用户ID',
  `status`          VARCHAR(20)  NOT NULL                COMMENT '运行状态：pending-等待 running-执行中 success-成功 failed-失败',
  `goal`            TEXT         DEFAULT NULL            COMMENT '本次运行目标描述',
  `intent`          VARCHAR(50)  DEFAULT NULL            COMMENT '识别到的用户意图：product_search/product_compare/shopping/chitchat',
  `start_time`      DATETIME     NOT NULL                COMMENT '开始时间',
  `end_time`        DATETIME     DEFAULT NULL            COMMENT '结束时间',
  `input`           TEXT         DEFAULT NULL            COMMENT '输入内容（用户消息）',
  `output`          TEXT         DEFAULT NULL            COMMENT '输出内容（AI 回复）',
  `error_message`   TEXT         DEFAULT NULL            COMMENT '错误信息（失败时记录）',
  `error_code`      VARCHAR(64)  DEFAULT NULL            COMMENT '错误码',
  `created_at`      DATETIME     NOT NULL                COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_status` (`status`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_intent` (`intent`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent运行记录表——记录每次 Agent 调度的完整过程';

-- ------------------------------------------------------------
-- 4.2 Agent 执行步骤表
-- 记录 Agent 运行中的每个步骤（规划、检索、调用工具等）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `agent_step`;
CREATE TABLE `agent_step` (
  `id`              VARCHAR(36)  NOT NULL                COMMENT '主键ID（UUID）',
  `run_id`          VARCHAR(36)  NOT NULL                COMMENT '所属运行ID，关联 agent_run.run_id',
  `step_type`       VARCHAR(64)  DEFAULT NULL            COMMENT '步骤类型：plan-规划 retrieve-检索 generate-生成 tool_call-工具调用',
  `step_name`       VARCHAR(100) NOT NULL                COMMENT '步骤名称，如：商品检索、意图分类、生成推荐话术',
  `status`          VARCHAR(20)  NOT NULL                COMMENT '步骤状态：pending/running/success/failed',
  `input`           TEXT         DEFAULT NULL            COMMENT '步骤输入',
  `output`          TEXT         DEFAULT NULL            COMMENT '步骤输出',
  `error_message`   TEXT         DEFAULT NULL            COMMENT '错误信息',
  `start_time`      DATETIME     NOT NULL                COMMENT '步骤开始时间',
  `end_time`        DATETIME     DEFAULT NULL            COMMENT '步骤结束时间',
  `duration_ms`     BIGINT       DEFAULT NULL            COMMENT '执行时长（毫秒）',
  `tool_call_id`    VARCHAR(64)  DEFAULT NULL            COMMENT '工具调用ID（tool_call 类型步骤关联）',
  `created_at`      DATETIME     NOT NULL                COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_step_name` (`step_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent执行步骤表——记录 Agent 运行的每个步骤';

-- ------------------------------------------------------------
-- 4.3 工具调用记录表
-- 记录 Agent 调用外部工具的详细信息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tool_call`;
CREATE TABLE `tool_call` (
  `id`              VARCHAR(36)  NOT NULL                COMMENT '主键ID（UUID）',
  `tool_call_id`    VARCHAR(36)  NOT NULL                COMMENT '工具调用唯一标识',
  `run_id`          VARCHAR(36)  NOT NULL                COMMENT '所属 Agent 运行ID',
  `tool_name`       VARCHAR(100) NOT NULL                COMMENT '工具名称，如：product_search、product_compare、image_understand',
  `input_params`    TEXT         DEFAULT NULL            COMMENT '输入参数（JSON 格式）',
  `output`          TEXT         DEFAULT NULL            COMMENT '输出结果（JSON 格式）',
  `status`          VARCHAR(20)  NOT NULL                COMMENT '执行状态：pending/success/failed',
  `duration_ms`     BIGINT       DEFAULT NULL            COMMENT '执行时长（毫秒）',
  `error_message`   TEXT         DEFAULT NULL            COMMENT '错误信息',
  `timestamp`       DATETIME     NOT NULL                COMMENT '执行时间',
  `created_at`      DATETIME     NOT NULL                COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_tool_name` (`tool_name`),
  KEY `idx_status` (`status`),
  KEY `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用记录表——记录 Agent 调用外部工具的详情';


-- ============================================================
-- 五、推荐与行为体系
-- ============================================================

-- ------------------------------------------------------------
-- 5.1 推荐日志表
-- 记录每次推荐的结果和用户反馈，用于效果分析
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `recommendation_log`;
CREATE TABLE `recommendation_log` (
  `id`                    BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '日志ID，主键自增',
  `user_id`               BIGINT   DEFAULT NULL             COMMENT '用户ID',
  `session_id`            VARCHAR(64) DEFAULT NULL          COMMENT '会话ID（关联 conversation.id）',
  `message_id`            BIGINT   DEFAULT NULL             COMMENT '触发推荐的消息ID',
  `query`                 TEXT     DEFAULT NULL              COMMENT '用户原始问题/需求描述',
  `intent`                VARCHAR(50) DEFAULT NULL           COMMENT '识别到的意图',
  `recommended_product_ids` JSON   DEFAULT NULL              COMMENT '推荐的商品ID列表 [1,5,8]',
  `recommend_reason`      TEXT     DEFAULT NULL              COMMENT 'Agent 的推荐理由',
  `agent_reasoning`       TEXT     DEFAULT NULL              COMMENT 'Agent 完整推理过程（用于调试和分析）',
  `user_clicked`          TINYINT(1) DEFAULT 0               COMMENT '用户是否点击了推荐商品：0-未点击 1-已点击',
  `user_feedback`         TINYINT  DEFAULT NULL              COMMENT '用户反馈：1-满意 0-不满意 NULL-未反馈',
  `create_time`           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_intent` (`intent`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐日志表——记录推荐结果与用户反馈，用于效果优化';

-- ------------------------------------------------------------
-- 5.2 用户浏览记录表
-- 记录用户在 App 中的浏览行为（商品详情页访问等）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_browse_history`;
CREATE TABLE `user_browse_history` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT              COMMENT '记录ID，主键自增',
  `user_id`     BIGINT   NOT NULL                             COMMENT '用户ID，关联 user.id',
  `product_id`  BIGINT   NOT NULL                             COMMENT '浏览的商品ID，关联 product.id',
  `source`      VARCHAR(30) DEFAULT 'recommend'               COMMENT '来源：recommend-AI推荐 search-搜索 browse-浏览 detail-详情页',
  `duration_sec`INT      DEFAULT 0                            COMMENT '浏览时长（秒）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP            COMMENT '浏览时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户浏览记录表——记录商品浏览行为';

-- ------------------------------------------------------------
-- 5.3 用户收藏表
-- 存储用户收藏/加入购物车的商品
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_favorite`;
CREATE TABLE `user_favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT              COMMENT '收藏ID，主键自增',
  `user_id`     BIGINT   NOT NULL                             COMMENT '用户ID，关联 user.id',
  `product_id`  BIGINT   NOT NULL                             COMMENT '商品ID，关联 product.id',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP            COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表——存储用户收藏的商品';


-- ============================================================
-- 六、知识库与日志体系（复用自 AgentCraft）
-- ============================================================

-- ------------------------------------------------------------
-- 6.1 问答日志表
-- 记录所有用户问答对，用于效果分析和未命中问题统计
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `qa_log`;
CREATE TABLE `qa_log` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT            COMMENT '日志ID，主键自增',
  `user_id`       BIGINT   DEFAULT NULL                       COMMENT '用户ID',
  `conversation_id` BIGINT DEFAULT NULL                       COMMENT '会话ID',
  `question`      TEXT     NOT NULL                            COMMENT '用户提问',
  `answer`        TEXT     NOT NULL                            COMMENT 'AI 回答',
  `task_type`     VARCHAR(50) DEFAULT 'unknown'               COMMENT '任务类型：shopping/product_search/product_compare/chitchat/unknown',
  `duration_ms`   BIGINT   DEFAULT NULL                       COMMENT '响应耗时（毫秒）',
  `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP          COMMENT '记录时间',
  `feedback_type` VARCHAR(20) DEFAULT NULL                    COMMENT '反馈类型：like/dislike',
  `feedback_time` DATETIME DEFAULT NULL                       COMMENT '反馈时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_task_type` (`task_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答日志表——记录所有问答对';

-- ------------------------------------------------------------
-- 6.2 未命中问题表
-- 记录 Agent 未能有效回答的问题，用于知识库补充
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `qa_unanswered`;
CREATE TABLE `qa_unanswered` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  `question`    VARCHAR(500) NOT NULL                 COMMENT '问题内容',
  `count`       INT          DEFAULT 1                COMMENT '累计提问次数',
  `last_user_id`BIGINT       DEFAULT NULL             COMMENT '最近提问的用户ID',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '首次提问时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question` (`question`),
  KEY `idx_count` (`count` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='未命中问题表——记录 Agent 未能有效回答的问题';

-- ------------------------------------------------------------
-- 6.3 知识库文档表
-- 存储 RAG 知识库的来源文档
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `knowledge_doc`;
CREATE TABLE `knowledge_doc` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '文档ID，主键自增',
  `doc_name`    VARCHAR(255) NOT NULL                 COMMENT '文档名称',
  `file_path`   VARCHAR(500) NOT NULL                 COMMENT '文档存储路径',
  `category_id` BIGINT       DEFAULT NULL             COMMENT '分类ID',
  `doc_type`    VARCHAR(30)  DEFAULT 'product'        COMMENT '文档类型：product-商品资料 review-评价 faq-问答 guide-导购话术',
  `status`      VARCHAR(20)  DEFAULT 'PENDING'        COMMENT '解析状态：PENDING-待解析 COMPLETED-已完成 FAILED-失败',
  `error_message` TEXT       DEFAULT NULL             COMMENT '解析失败的错误原因',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_doc_type` (`doc_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表——存储 RAG 来源文档';

-- ------------------------------------------------------------
-- 6.4 文档切片表
-- 存储文档切分后的文本片段，用于向量检索
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `knowledge_chunk`;
CREATE TABLE `knowledge_chunk` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT              COMMENT '主键ID',
  `doc_id`      BIGINT   NOT NULL                             COMMENT '来源文档ID，关联 knowledge_doc.id',
  `product_id`  BIGINT   DEFAULT NULL                         COMMENT '关联商品ID（商品类文档可选关联）',
  `chunk_text`  TEXT     NOT NULL                              COMMENT '切片文本内容',
  `chunk_index` INT      DEFAULT 0                            COMMENT '切片在文档中的顺序',
  `chunk_type`  VARCHAR(30) DEFAULT 'text'                    COMMENT '切片类型：text-文本 review-评价 faq-问答',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_chunk_type` (`chunk_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档切片表——存储切分后的文本片段';

-- ------------------------------------------------------------
-- 6.5 通知公告表
-- 存储系统通知和运营公告
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `notice`;
CREATE TABLE `notice` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  `title`       VARCHAR(255) NOT NULL                 COMMENT '通知标题',
  `content`     TEXT         DEFAULT NULL             COMMENT '通知内容',
  `notice_type` VARCHAR(20)  DEFAULT 'system'         COMMENT '通知类型：system-系统 activity-活动 promotion-促销',
  `file_path`   VARCHAR(500) DEFAULT NULL             COMMENT '附件路径',
  `is_active`   TINYINT(1)   DEFAULT 1                COMMENT '是否有效：1-有效 0-无效',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知公告表';


-- ============================================================
-- 七、测试数据
-- ============================================================

-- 测试用户
INSERT INTO `user` (`username`, `phone`, `password`, `gender`, `skin_type`) VALUES
  ('测试用户A', '13800000001', '$2a$10$NPgK2tlqMkMv6OU7PnGJ8.1LWxjYMdrDMatWZrAlly3wBr9D2/o0W', 2, '油性'),
  ('测试用户B', '13800000002', '$2a$10$b.TnVwf70kod7jYxeU284u4KcVOJiDebzcKzyFnXaB2bVlqfwiilO', 1, NULL);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 完成！共 22 张表
-- 用户体系：user, admin
-- 商品体系：category, product, product_sku, product_image, product_review, product_faq
-- 对话体系：conversation, message, conversation_context
-- Agent体系：agent_run, agent_step, tool_call
-- 推荐体系：recommendation_log, user_browse_history, user_favorite
-- 知识体系：qa_log, qa_unanswered, knowledge_doc, knowledge_chunk, notice
-- ============================================================
