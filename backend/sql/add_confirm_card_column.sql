-- 购物车确认卡片功能：给 message 表添加 confirm_card 字段
-- 执行方式：mysql -u root -p shop_agent < add_confirm_card_column.sql

ALTER TABLE message
ADD COLUMN confirm_card JSON DEFAULT NULL COMMENT '确认卡片 JSON，购物车操作确认用：{message, action, product, buttons}'
AFTER product_cards;
