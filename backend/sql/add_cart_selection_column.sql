-- 购物车列表/选择卡片功能：给 message 表添加 cart_selection 字段
-- 执行方式：mysql -u root -p shop_agent < add_cart_selection_column.sql

ALTER TABLE message
ADD COLUMN cart_selection JSON DEFAULT NULL COMMENT '购物车选择/列表卡片 JSON，用于查看购物车和批量加购展示'
AFTER confirm_card;
