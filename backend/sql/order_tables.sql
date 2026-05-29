-- ============================================================
-- 订单体系 - 新增表
-- 基于 MySQL 8.0，字符集 utf8mb4
-- ============================================================

USE shop_agent_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 订单主表
-- 存储用户订单核心信息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '订单ID，主键自增',
  `user_id`         BIGINT        NOT NULL                 COMMENT '下单用户ID，关联 user.id',
  `order_no`        VARCHAR(30)   NOT NULL                 COMMENT '订单编号，格式：yyyyMMddHHmmss + 6位随机数',
  `status`          TINYINT       DEFAULT 0                COMMENT '订单状态：0-待付款 1-待发货 2-待收货 3-已完成 4-已取消',
  `total_amount`    DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '商品总金额',
  `pay_amount`      DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '实付金额（含运费）',
  `freight_amount`  DECIMAL(10,2) DEFAULT 0.00             COMMENT '运费',
  `address_id`      BIGINT        DEFAULT NULL             COMMENT '收货地址ID，关联 address.id',
  `receiver_name`   VARCHAR(50)   DEFAULT NULL             COMMENT '收货人姓名（快照）',
  `receiver_phone`  VARCHAR(20)   DEFAULT NULL             COMMENT '收货人电话（快照）',
  `receiver_address`VARCHAR(500)  DEFAULT NULL             COMMENT '收货地址（快照）',
  `remark`          VARCHAR(500)  DEFAULT NULL             COMMENT '订单备注',
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time`        DATETIME      DEFAULT NULL             COMMENT '支付时间',
  `delivery_time`   DATETIME      DEFAULT NULL             COMMENT '发货时间',
  `receive_time`    DATETIME      DEFAULT NULL             COMMENT '收货时间',
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表——存储用户订单核心信息';

-- ------------------------------------------------------------
-- 订单商品表
-- 存储订单中的商品明细（快照，不受商品后续修改影响）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '订单项ID，主键自增',
  `order_id`        BIGINT        NOT NULL                 COMMENT '所属订单ID，关联 order.id',
  `product_id`      BIGINT        NOT NULL                 COMMENT '商品ID',
  `product_title`   VARCHAR(500)  NOT NULL                 COMMENT '商品标题（快照）',
  `product_image`   VARCHAR(500)  DEFAULT NULL             COMMENT '商品图片（快照）',
  `sku_id`          BIGINT        DEFAULT NULL             COMMENT 'SKU ID',
  `sku_properties`  VARCHAR(500)  DEFAULT NULL             COMMENT '规格属性文本（快照），如：容量: 30ml 颜色: 黑色',
  `price`           DECIMAL(10,2) NOT NULL                 COMMENT '商品单价（快照）',
  `quantity`        INT           NOT NULL DEFAULT 1       COMMENT '购买数量',
  `total_amount`    DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '小计金额',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商品表——存储订单商品明细快照';

SET FOREIGN_KEY_CHECKS = 1;
