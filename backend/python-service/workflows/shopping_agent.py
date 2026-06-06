from typing import Dict, Any, Optional, Generator, List
from datetime import datetime
from workflows.base_agent import BaseAgent
from core.vector_store import vector_store
from core.llm import LLMService
from core.mysql_client import mysql_client
from tools.registry import tool_registry
import logging
import json
import time

logger = logging.getLogger(__name__)


class ShoppingAgent(BaseAgent):
    """导购 Agent - 商品推荐、对比、搜索"""

    # 缓存每个会话最近推荐的商品，供购物车操作时使用
    _recent_products: Dict[str, List[Dict]] = {}
    # 缓存待确认的购物车操作（删除/修改/清空需用户确认）
    _pending_cart_actions: Dict[str, Dict] = {}
    # 缓存购物车状态（避免同一会话内重复请求 Java API）
    _cart_cache: Dict[str, Dict] = {}  # {conversation_id: {"items": [...], "ts": timestamp}}

    def __init__(self):
        self.vector_store = vector_store
        self.llm_service = LLMService()

    @staticmethod
    def _get_current_season() -> str:
        """根据当前月份返回季节信息，用于推荐话术的季节感知"""
        month = datetime.now().month
        if month in (3, 4, 5):
            return "春季"
        elif month in (6, 7, 8):
            return "夏季"
        elif month in (9, 10, 11):
            return "秋季"
        else:
            return "冬季"

    @staticmethod
    def _detect_comparison(question: str) -> bool:
        """检测是否为多商品对比意图，如'A和B哪个好'"""
        import re
        patterns = [
            r'.+和.+哪个好', r'.+跟.+哪个好', r'.+与.+哪个好',
            r'.+和.+对比', r'.+跟.+对比', r'.+与.+对比',
            r'.+和.+比较', r'.+跟.+比较', r'.+与.+比较',
            r'.+和.+区别', r'.+跟.+区别', r'.+与.+区别',
            r'.+和.+选哪个', r'.+跟.+选哪个', r'.+与.+选哪个',
            r'对比.+和.+', r'比较.+和.+',
        ]
        return any(re.search(pat, question) for pat in patterns)

    @staticmethod
    def _extract_comparison_products(question: str) -> List[str]:
        """从对比问题中提取商品名称，如'iPhone和华为哪个好' → ['iPhone', '华为']"""
        import re
        # 匹配 "A和B" / "A跟B" / "A与B"
        match = re.search(r'(.+?)[和跟与](.+?)(?:哪个好|对比|比较|区别|选哪个)', question)
        if match:
            p1 = match.group(1).strip()
            p2 = match.group(2).strip()
            # 清理前缀词
            for prefix in ["对比", "比较", "帮我看看", "帮我比比", "我想知道", "请帮我"]:
                p1 = p1.removeprefix(prefix)
                p2 = p2.removeprefix(prefix)
            return [p1, p2] if p1 and p2 else []
        # 匹配 "对比A和B" / "比较A和B"
        match = re.search(r'(?:对比|比较)(.+?)[和跟与](.+)', question)
        if match:
            p1 = match.group(1).strip()
            p2 = match.group(2).strip()
            return [p1, p2] if p1 and p2 else []
        return []

    def _handle_comparison(self, question: str, user_profile: str = "") -> Dict[str, Any]:
        """处理多商品对比请求"""
        product_names = self._extract_comparison_products(question)
        if len(product_names) < 2:
            return None  # 无法提取两个商品，走正常推荐流程

        # 搜索每个商品
        all_products = []
        for name in product_names:
            products = self._search_products(name, user_profile)
            if products:
                all_products.append({"name": name, "products": products})

        if len(all_products) < 2:
            return None  # 找不到足够商品，走正常推荐流程

        # 取每个搜索结果的最佳匹配
        compare_items = []
        for item in all_products:
            best = item["products"][0]  # 已按综合评分排序
            compare_items.append(best)

        # 构建对比卡片
        product_cards = self._build_product_cards(compare_items)

        # 用 LLM 生成对比话术
        compare_context = "\n".join([
            f"商品{i+1}：{p.get('title', '')} | 品牌:{p.get('brand', '')} | "
            f"价格:¥{p.get('base_price', 0)} | 评分:{p.get('rating', 0)} | "
            f"销量:{p.get('sales_count', 0)} | 标签:{p.get('tags', '')}"
            for i, p in enumerate(compare_items)
        ])

        prompt = (
            f"你是智能导购助手「小智」。用户想对比以下商品，请给出简洁的对比分析。\n\n"
            f"用户问题：{question}\n\n"
            f"商品信息：\n{compare_context}\n\n"
            f"回复规则（严格遵守）：\n"
            f"1. 控制在120字以内\n"
            f"2. 从价格、评分、销量等维度简要对比\n"
            f"3. 给出明确推荐建议\n"
            f"4. 不要编造商品不存在的功能\n"
        )

        try:
            answer = self.llm_service.llm.invoke(prompt).content
        except Exception as e:
            logger.error(f"[ShoppingAgent] Comparison LLM error: {e}")
            names = [p.get('title', '') for p in compare_items]
            answer = f"为您对比了{'和'.join(names)}，请查看下方商品卡片了解详细信息。"

        return {
            "answer": answer,
            "sources": [],
            "has_sources": False,
            "task_type": "shopping",
            "product_cards": product_cards
        }

    def recommend(self, question: str, conversation_id: Optional[str] = None,
                  user_id: Optional[str] = None, context: str = "",
                  user_profile: str = "", **kwargs) -> Dict[str, Any]:
        """处理导购请求：检索商品 → 生成推荐话术 + 商品卡片"""
        logger.info(f"[ShoppingAgent] Processing: {question[:50]}...")

        try:
            # 0. 检测对比意图
            if self._detect_comparison(question):
                result = self._handle_comparison(question, user_profile)
                if result:
                    return result
            # 1. 读取会话记忆
            conversation_history = ""
            if conversation_id and tool_registry.has_tool("conversation_memory_read"):
                try:
                    history = tool_registry.invoke_tool(
                        "conversation_memory_read",
                        {"conversation_id": conversation_id, "limit": 10}
                    )
                    messages = history.get("messages", [])
                    if messages:
                        conversation_history = self._format_history(messages)
                except Exception as e:
                    logger.warning(f"[ShoppingAgent] Failed to read memory: {e}")

            full_context = context
            if conversation_history:
                full_context = f"{context}\n\n{conversation_history}" if context else conversation_history

            # 2. 向量检索相关商品知识
            docs = self.vector_store.search(
                query=question, k=8, similarity_threshold=0.6, use_rerank=False
            )
            logger.info(f"[ShoppingAgent] Retrieved {len(docs)} documents")

            # 3. 从 MySQL 搜索匹配商品（传入对话上下文，帮助理解"不要XX"等承接上文的查询）
            products = self._search_products(question, user_profile, conversation_context=full_context)
            logger.info(f"[ShoppingAgent] Found {len(products)} products from DB")

            # 4. 构建商品信息上下文
            product_context = self._build_product_context(products)

            # 5. 合并所有上下文
            rag_context = "\n".join([
                getattr(doc, 'page_content', '') if hasattr(doc, 'page_content')
                else doc.get('page_content', '') if isinstance(doc, dict) else str(doc)
                for doc in docs
            ])
            combined_context = f"{full_context}\n\n{product_context}" if full_context else product_context

            # 6. 用 LLM 生成推荐话术
            answer = self._generate_recommendation(question, combined_context, products, user_profile)

            # 7. 构建商品卡片
            product_cards = self._build_product_cards(products)

            # 8. 缓存推荐商品，供购物车操作时使用
            if conversation_id and products:
                ShoppingAgent._recent_products[conversation_id] = products

            sources = self._build_sources(docs)

            return {
                "answer": answer,
                "sources": sources,
                "has_sources": len(sources) > 0,
                "task_type": "shopping",
                "product_cards": product_cards
            }

        except Exception as e:
            logger.error(f"[ShoppingAgent] Error: {e}", exc_info=True)
            return {
                "answer": "抱歉，为您查找商品时遇到了问题，请稍后再试。",
                "sources": [],
                "has_sources": False,
                "task_type": "shopping",
                "product_cards": [],
                "error": True
            }

    def recommend_stream(self, question: str, conversation_id: Optional[str] = None,
                         user_id: Optional[str] = None, context: str = "",
                         user_profile: str = "", **kwargs) -> Generator[str, None, None]:
        """流式导购：先检索商品，再流式生成推荐话术"""
        logger.info(f"[ShoppingAgent] Stream processing: {question[:50]}...")

        try:
            # 检测对比意图
            if self._detect_comparison(question):
                result = self._handle_comparison(question, user_profile)
                if result:
                    yield json.dumps({"type": "routed", "task_type": "shopping"})
                    yield json.dumps({"type": "token", "content": result.get("answer", "")})
                    yield json.dumps({
                        "type": "product_cards",
                        "product_cards": result.get("product_cards", []),
                        "sources": [],
                        "task_type": "shopping"
                    })
                    yield json.dumps({"type": "end", "content": result.get("answer", "")})
                    return
            # 检索
            docs = self.vector_store.search(
                query=question, k=8, similarity_threshold=0.6, use_rerank=False
            )
            products = self._search_products(question, user_profile, conversation_context=context)
            product_context = self._build_product_context(products)
            sources = self._build_sources(docs)
            logger.info(f"[ShoppingAgent] Stream: found {len(products)} products, {len(docs)} docs")

            # 流式生成 - 在 end 之前插入商品卡片
            product_cards = self._build_product_cards(products)
            logger.info(f"[ShoppingAgent] Stream: built {len(product_cards)} product cards")

            # 缓存推荐商品，供购物车操作时使用
            if conversation_id and products:
                ShoppingAgent._recent_products[conversation_id] = products

            for chunk in self.llm_service.get_answer_stream(
                question=question,
                context_docs=docs,
                conversation_context=f"{context}\n\n{product_context}" if context else product_context,
                user_profile=user_profile,
                season=self._get_current_season()
            ):
                # 在 end 事件之前插入商品卡片
                try:
                    parsed = json.loads(chunk)
                    if parsed.get("type") == "end":
                        logger.info(f"[ShoppingAgent] Yielding product_cards event with {len(product_cards)} cards")
                        yield json.dumps({
                            "type": "product_cards",
                            "product_cards": product_cards,
                            "sources": sources,
                            "task_type": "shopping"
                        })
                except (json.JSONDecodeError, AttributeError):
                    pass
                yield chunk

        except Exception as e:
            logger.error(f"[ShoppingAgent] Stream error: {e}", exc_info=True)
            yield json.dumps({
                "type": "error",
                "content": "为您查找商品时遇到问题，请稍后再试。"
            })

    def handle_cart(self, question: str, conversation_id: Optional[str] = None,
                    user_id: Optional[str] = None, context: str = "", **kwargs) -> Dict[str, Any]:
        """处理购物车操作请求（同步）"""
        jwt_token = kwargs.get("jwt_token")
        logger.info(f"[ShoppingAgent] Cart handling: {question[:50]}...")

        # 内部添加确认消息（Android 端已通过 Java API 添加商品）
        if question.startswith("ADD_CONFIRM:"):
            count = question.split(":")[1] if ":" in question else "1"
            if conversation_id:
                self._invalidate_cart_cache(conversation_id)
            answer = self._generate_add_confirm_response(count, context, user_id)
            return {
                "answer": answer,
                "sources": [], "has_sources": False,
                "task_type": "cart", "product_cards": []
            }

        if not user_id:
            return {
                "answer": "请先登录后再操作购物车哦～",
                "sources": [], "has_sources": False,
                "task_type": "cart", "product_cards": []
            }

        try:
            # 0. 检查是否有待确认的操作
            pending = ShoppingAgent._pending_cart_actions.get(conversation_id)
            if pending:
                return self._handle_confirmation(question, pending, user_id, conversation_id, jwt_token)

            # 0.1 获取购物车列表（通过 Java API，带会话缓存）
            cart_items = self._fetch_cart_items(user_id, jwt_token, conversation_id)

            # 1. LLM 判断子意图 + 提取参数
            action, product_id, quantity, product_name = self._parse_cart_intent(question, context, user_id, cart_items)
            logger.info(f"[Cart] Parsed: action={action}, product_id={product_id}, name={product_name}, qty={quantity}")

            # 2. add 操作：如果用户没有明确指定商品名，显示选择卡片
            if action == "add":
                user_specified_product = self._user_mentioned_product(question)
                if not user_specified_product:
                    recent = ShoppingAgent._recent_products.get(conversation_id, [])
                    if recent:
                        selection = self._build_cart_selection(recent)
                        return {
                            "answer": selection["message"],
                            "sources": [], "has_sources": False,
                            "task_type": "cart", "product_cards": [],
                            "cart_selection": selection
                        }
                    else:
                        products = self._search_products(question)
                        if products:
                            selection = self._build_cart_selection(products)
                            return {
                                "answer": selection["message"],
                                "sources": [], "has_sources": False,
                                "task_type": "cart", "product_cards": [],
                                "cart_selection": selection
                            }

            # 3. 解析 product_id（从名称反查数据库）
            if product_id is None and product_name:
                product_id = self._resolve_product_id(product_name)

            # 3.0.5 按位置删除检测："第N个" → 精确匹配购物车中第N项的 cart_item_id
            cart_item_id = None
            if action == "remove" and product_name:
                import re
                pos_match = re.search(r'第(\d+)[个件]', product_name)
                if pos_match and cart_items:
                    pos = int(pos_match.group(1))
                    if 1 <= pos <= len(cart_items):
                        cart_item_id = cart_items[pos - 1].get("id")
                        logger.info(f"[Cart] Position-based remove: pos={pos}, cart_item_id={cart_item_id}")

            # 3.1 批量删除检测（返回 cart_item_id 列表）
            batch_cart_item_ids = None
            if action == "remove" and product_id is None:
                batch_cart_item_ids = self._detect_batch_remove(product_name, user_id, cart_items, jwt_token)
                if batch_cart_item_ids:
                    logger.info(f"[Cart] Batch remove detected: {len(batch_cart_item_ids)} items")

            # 3.2 remove/update 需要 product_id，缺失时询问用户（clear/list/批量不需要）
            if action not in ("list", "clear") and product_id is None and not batch_cart_item_ids and not cart_item_id:
                clarification = self._ask_which_product(action, question, user_id, context, conversation_id, cart_items, jwt_token)
                return {
                    "answer": clarification,
                    "sources": [], "has_sources": False,
                    "task_type": "cart", "product_cards": []
                }

            # 4. 删除/修改/清空需要确认（返回确认卡片）
            if action in ("remove", "update", "clear"):
                if batch_cart_item_ids:
                    ShoppingAgent._pending_cart_actions[conversation_id] = {
                        "action": action, "cart_item_ids": batch_cart_item_ids,
                        "user_id": user_id, "jwt_token": jwt_token
                    }
                    confirm_card = self._build_batch_confirm_message(batch_cart_item_ids, user_id, cart_items)
                elif cart_item_id:
                    # 按位置删除：传 cart_item_id 精确删除单条记录
                    ShoppingAgent._pending_cart_actions[conversation_id] = {
                        "action": action, "cart_item_id": cart_item_id,
                        "product_id": product_id,
                        "user_id": user_id, "jwt_token": jwt_token
                    }
                    confirm_card = self._build_confirm_message(action, product_id, product_name)
                else:
                    ShoppingAgent._pending_cart_actions[conversation_id] = {
                        "action": action, "product_id": product_id,
                        "quantity": quantity, "user_id": user_id, "jwt_token": jwt_token
                    }
                    confirm_card = self._build_confirm_message(action, product_id, product_name)
                return {
                    "answer": confirm_card.get("message", "请确认操作"),
                    "sources": [], "has_sources": False,
                    "task_type": "cart", "product_cards": [],
                    "confirm_card": confirm_card
                }

            # 5. list 操作返回商品卡片
            if action == "list":
                cart_cards = self._build_cart_list_cards(user_id, cart_items, jwt_token)
                return {
                    "answer": cart_cards.get("message", "购物车里的商品："),
                    "sources": [], "has_sources": False,
                    "task_type": "cart", "product_cards": [],
                    "cart_selection": cart_cards
                }

            # 6. 执行购物车操作（add 不需要确认）
            result = self._execute_cart_action(action, user_id, product_id, quantity, jwt_token, conversation_id)

            # 7. 组装自然语言回复
            answer = self._generate_cart_response(question, action, result, context)

            return {
                "answer": answer,
                "sources": [], "has_sources": False,
                "task_type": "cart", "product_cards": []
            }

        except Exception as e:
            logger.error(f"[ShoppingAgent] Cart error: {e}", exc_info=True)
            return {
                "answer": "操作购物车时遇到问题，请稍后再试。",
                "sources": [], "has_sources": False,
                "task_type": "cart", "product_cards": [], "error": True
            }

    def handle_cart_stream(self, question: str, conversation_id: Optional[str] = None,
                           user_id: Optional[str] = None, context: str = "",
                           **kwargs) -> Generator[str, None, None]:
        """处理购物车操作请求（流式）。

        处理流程：
        1. 检查是否有待确认操作（删除/修改需二次确认）
        2. 获取购物车列表（带 30s 会话缓存）
        3. LLM 解析购物车子意图（add/list/remove/update/clear）
        4. add 操作：泛化指令显示选择卡片，明确商品直接添加
        5. remove/update 操作：返回确认卡片，用户确认后执行
        6. list 操作：返回购物车列表卡片
        7. 执行操作 + 生成回复

        SSE 事件类型：
        - routed: 路由到购物车处理
        - token: 文本回复片段
        - confirm_card: 确认卡片（删除/修改确认）
        - cart_selection: 选择卡片（批量加购勾选）
        - cart_list: 购物车列表卡片
        - end: 流结束
        """
        jwt_token = kwargs.get("jwt_token")
        logger.info(f"[ShoppingAgent] Cart stream handling: {question[:50]}...")

        # 内部添加确认消息
        if question.startswith("ADD_CONFIRM:"):
            count = question.split(":")[1] if ":" in question else "1"
            # Android 端已通过 Java API 添加商品，清除缓存确保下次查看购物车获取最新数据
            if conversation_id:
                self._invalidate_cart_cache(conversation_id)
            yield json.dumps({"type": "routed", "task_type": "cart"})
            yield json.dumps({"type": "token", "content": f"成功添加{count}件商品到购物车"})
            yield json.dumps({"type": "end"})
            return

        try:
            yield json.dumps({"type": "routed", "task_type": "cart"})

            if not user_id:
                yield json.dumps({"type": "token", "content": "请先登录后再操作购物车哦～"})
                yield json.dumps({"type": "end"})
                return

            # 0. 检查是否有待确认的操作
            pending = ShoppingAgent._pending_cart_actions.get(conversation_id)
            logger.info(f"[Cart] Checking pending: conv_id={conversation_id}, found={pending is not None}, all_keys={list(ShoppingAgent._pending_cart_actions.keys())}")
            if pending:
                result = self._handle_confirmation(question, pending, user_id, conversation_id, jwt_token)
                yield json.dumps({"type": "token", "content": result["answer"]})
                yield json.dumps({"type": "end"})
                return

            # 0.1 获取购物车列表（通过 Java API，带会话缓存）
            cart_items = self._fetch_cart_items(user_id, jwt_token, conversation_id)

            # 1. 解析意图
            action, product_id, quantity, product_name = self._parse_cart_intent(question, context, user_id, cart_items)
            logger.info(f"[Cart Stream] Parsed: action={action}, product_id={product_id}, name={product_name}, qty={quantity}")

            # 2. add 操作：如果用户没有明确指定商品名，显示选择卡片（即使 LLM 从上下文提取了商品）
            if action == "add":
                user_specified_product = self._user_mentioned_product(question)
                if not user_specified_product:
                    recent = ShoppingAgent._recent_products.get(conversation_id, [])
                    if recent:
                        selection = self._build_cart_selection(recent)
                        yield json.dumps({"type": "cart_selection", "cart_selection": selection})
                        yield json.dumps({"type": "end"})
                        return
                    else:
                        # 无最近推荐缓存，搜索商品供用户选择
                        products = self._search_products(question)
                        if products:
                            selection = self._build_cart_selection(products)
                            yield json.dumps({"type": "cart_selection", "cart_selection": selection})
                            yield json.dumps({"type": "end"})
                            return

            # 3. 解析 product_id（从名称反查数据库）
            if product_id is None and product_name:
                product_id = self._resolve_product_id(product_name)

            # 3.0.5 按位置删除检测："第N个" → 精确匹配购物车中第N项的 cart_item_id
            cart_item_id = None
            if action == "remove" and product_name:
                import re
                pos_match = re.search(r'第(\d+)[个件]', product_name)
                if pos_match and cart_items:
                    pos = int(pos_match.group(1))
                    if 1 <= pos <= len(cart_items):
                        cart_item_id = cart_items[pos - 1].get("id")
                        logger.info(f"[Cart] Position-based remove: pos={pos}, cart_item_id={cart_item_id}")

            # 3.1 批量删除检测（返回 cart_item_id 列表）
            batch_cart_item_ids = None
            if action == "remove" and product_id is None:
                batch_cart_item_ids = self._detect_batch_remove(product_name, user_id, cart_items, jwt_token)
                if batch_cart_item_ids:
                    logger.info(f"[Cart Stream] Batch remove detected: {len(batch_cart_item_ids)} items")

            # 3.2 缺失 product_id 时询问用户（clear/list/批量不需要）
            if action not in ("list", "clear") and product_id is None and not batch_cart_item_ids and not cart_item_id:
                clarification = self._ask_which_product(action, question, user_id, context, conversation_id, cart_items, jwt_token)
                yield json.dumps({"type": "token", "content": clarification})
                yield json.dumps({"type": "end"})
                return

            # 4. 删除/修改/清空需要确认（返回确认卡片）
            if action in ("remove", "update", "clear"):
                if batch_cart_item_ids:
                    # 批量删除：存入 cart_item_ids
                    ShoppingAgent._pending_cart_actions[conversation_id] = {
                        "action": action, "cart_item_ids": batch_cart_item_ids,
                        "user_id": user_id, "jwt_token": jwt_token
                    }
                    logger.info(f"[Cart] Stored pending batch remove: conv_id={conversation_id}, cart_item_ids={batch_cart_item_ids}, user_id={user_id}")
                    confirm_card = self._build_batch_confirm_message(batch_cart_item_ids, user_id, cart_items)
                elif cart_item_id:
                    # 按位置删除：传 cart_item_id 精确删除单条记录
                    ShoppingAgent._pending_cart_actions[conversation_id] = {
                        "action": action, "cart_item_id": cart_item_id,
                        "product_id": product_id,
                        "user_id": user_id, "jwt_token": jwt_token
                    }
                    logger.info(f"[Cart] Stored pending position remove: conv_id={conversation_id}, cart_item_id={cart_item_id}, user_id={user_id}")
                    confirm_card = self._build_confirm_message(action, product_id, product_name)
                else:
                    ShoppingAgent._pending_cart_actions[conversation_id] = {
                        "action": action, "product_id": product_id,
                        "quantity": quantity, "user_id": user_id, "jwt_token": jwt_token
                    }
                    logger.info(f"[Cart] Stored pending single remove: conv_id={conversation_id}, pid={product_id}, qty={quantity}, user_id={user_id}")
                    confirm_card = self._build_confirm_message(action, product_id, product_name)
                yield json.dumps({"type": "confirm_card", "confirm_card": confirm_card})
                yield json.dumps({"type": "end"})
                return

            # 5. list 操作返回商品卡片
            if action == "list":
                cart_cards = self._build_cart_list_cards(user_id, cart_items, jwt_token)
                yield json.dumps({"type": "token", "content": cart_cards.get("message", "购物车里的商品：")})
                yield json.dumps({"type": "cart_list", "cart_list": cart_cards})
                yield json.dumps({"type": "end"})
                return

            # 6. 执行操作（add 不需要确认）
            result = self._execute_cart_action(action, user_id, product_id, quantity, jwt_token, conversation_id)

            # 7. 流式输出回复
            answer = self._generate_cart_response(question, action, result, context)
            yield json.dumps({"type": "token", "content": answer})
            yield json.dumps({"type": "end"})

        except Exception as e:
            logger.error(f"[ShoppingAgent] Cart stream error: {e}", exc_info=True)
            yield json.dumps({"type": "error", "content": "操作购物车时遇到问题，请稍后再试。"})

    def _fetch_cart_items(self, user_id: str, jwt_token: str = None,
                          conversation_id: str = None, force_refresh: bool = False) -> list:
        """通过 Java API 获取购物车列表，支持会话级缓存（30秒 TTL）"""
        # 检查缓存
        if not force_refresh and conversation_id:
            cached = ShoppingAgent._cart_cache.get(conversation_id)
            if cached and (time.time() - cached.get("ts", 0)) < 30:
                return cached.get("items", [])

        try:
            cart_tool = tool_registry.get_tool("cart_operation")
            if not cart_tool:
                logger.warning("[ShoppingAgent] cart_operation tool not registered")
                return []
            result = cart_tool.execute({"action": "list", "user_id": user_id, "jwt_token": jwt_token})
            if not result.get("success"):
                logger.warning(f"[ShoppingAgent] _fetch_cart_items failed: {result.get('message')}")
                return []
            items = result.get("data", {}).get("items", [])
            # 只有非空结果才更新缓存，避免因临时错误缓存空列表
            if conversation_id and items:
                ShoppingAgent._cart_cache[conversation_id] = {"items": items, "ts": time.time()}
            return items
        except Exception as e:
            logger.warning(f"[ShoppingAgent] _fetch_cart_items failed: {e}")
            return []

    def _invalidate_cart_cache(self, conversation_id: str):
        """购物车操作后清除缓存，确保下次获取最新状态"""
        ShoppingAgent._cart_cache.pop(conversation_id, None)

    def _parse_cart_intent(self, question: str, context: str = "", user_id: str = None, cart_items: list = None) -> tuple:
        """用 LLM 解析购物车子意图。

        将用户自然语言转化为结构化操作：
        - "加到购物车" → action="add"
        - "查看购物车" → action="list"
        - "删除第二个" → action="remove", product_name="前2个"
        - "清空购物车" → action="clear"

        Args:
            question: 用户问题
            context: 对话上下文
            user_id: 用户 ID
            cart_items: 预取的购物车列表（帮助 LLM 理解"第N个"引用）

        Returns:
            (action, product_id, quantity, product_name) 四元组
        """
        # 使用预取的购物车内容，帮助 LLM 理解"第N个""最后一个"等引用
        cart_context = ""
        if cart_items:
            cart_lines = [f"{it['index']}. {it['title']} (id={it['product_id']}, 数量={it['quantity']})" for it in cart_items]
            cart_context = "当前购物车内容：\n" + "\n".join(cart_lines)

        prompt = (
            "你是购物车助手。分析用户输入和对话上下文，返回 JSON：\n"
            '{"action": "add|list|remove|update|clear", "product_id": 数字或null, '
            '"product_name": "商品名称或null", "quantity": 数字或null}\n\n'
            "规则：\n"
            "- \"加到购物车/加购/加入购物车\" → action=add\n"
            "- \"查看购物车/购物车里有什么/看看购物车\" → action=list\n"
            "- \"删除/移除购物车里的XX\" → action=remove\n"
            "- \"改数量/改为N个/修改数量\" → action=update\n"
            "- \"清空购物车/清理购物车/清除购物车/购物车清空\" → action=clear\n"
            "- 从对话上下文中提取最近推荐或提到的商品名称，填入 product_name\n"
            "- 如果用户说\"第一个/第二个/第N个\"，把\"第N个\"填入 product_name（用阿拉伯数字，如\"第1个\"）\n"
            "- 如果用户说\"最后一个/最后N个\"，根据购物车列表的最后一个/最后N个确定商品\n"
            "- 如果用户说\"前N个/前N件/后N个/后N件\"（如\"删除前两个\"），把\"前2个\"填入 product_name（用阿拉伯数字）\n"
            "- 如果能确定具体商品 ID，填入 product_id；否则填 product_name\n"
            "- 只返回JSON，不要其他内容\n\n"
            f"用户输入：{question}\n"
            f"对话上下文：{context or '（无）'}\n"
            f"{cart_context}"
        )

        try:
            result = self.llm_service.llm.invoke(prompt)
            text = result.content if hasattr(result, 'content') else str(result)
            import re
            json_match = re.search(r'\{[^}]+\}', text.strip())
            if json_match:
                data = json.loads(json_match.group())
            else:
                data = json.loads(text.strip())

            action = data.get("action", "list")
            product_id = data.get("product_id")
            product_name = data.get("product_name")
            quantity = data.get("quantity")

            valid_actions = {"add", "list", "remove", "update", "clear"}
            if action not in valid_actions:
                action = "list"

            return action, product_id, quantity, product_name

        except Exception as e:
            logger.warning(f"[ShoppingAgent] Cart intent parse failed: {e}, defaulting to list")
            return "list", None, None, None

    def _user_mentioned_product(self, question: str) -> bool:
        """判断用户问题中是否明确提到了商品名（而非仅仅说'添加购物车'等泛化指令）"""
        import re
        # 去掉购物车操作关键词后，看是否还有实质性商品名
        cart_keywords = r'(添加|加入|加到|放进|放入|购物车|加购|购买|买|下单)'
        cleaned = re.sub(cart_keywords, '', question).strip()
        # 如果去掉操作词后剩余内容 >= 2 个字，认为用户提到了具体商品
        # 例如 "添加小米手机到购物车" → "小米手机" (有商品)
        # 例如 "添加购物车" → "" (无商品)
        # 例如 "加到购物车" → "" (无商品)
        # 例如 "帮我加购" → "" (无商品)
        return len(cleaned) >= 2

    def _resolve_product_id(self, product_name: str) -> Optional[int]:
        """通过商品名称从数据库反查 product_id"""
        if not product_name:
            return None
        try:
            product = mysql_client.fetch_one(
                "SELECT id FROM product WHERE title LIKE %s AND status = 1 LIMIT 1",
                (f"%{product_name}%",)
            )
            if product:
                logger.info(f"[ShoppingAgent] Resolved product_id={product['id']} for '{product_name}'")
                return product["id"]
            logger.info(f"[ShoppingAgent] No product found for '{product_name}'")
        except Exception as e:
            logger.warning(f"[ShoppingAgent] Product resolve failed: {e}")
        return None

    def _ask_which_product(self, action: str, question: str, user_id: str,
                           context: str = "", conversation_id: str = None,
                           cart_items: list = None, jwt_token: str = None) -> str:
        """当无法确定具体商品时，询问用户选择"""
        if action == "add":
            # 优先使用最近推荐的商品缓存
            products = ShoppingAgent._recent_products.get(conversation_id, [])
            if not products:
                # 从上下文中提取关键词搜索
                products = self._search_products(question)
            if products:
                lines = ["您想添加哪一款到购物车呢？"]
                for i, p in enumerate(products, 1):
                    lines.append(
                        f"{i}. {p.get('title', '未知')} "
                        f"¥{p.get('base_price', 0)}"
                    )
                lines.append("\n请告诉我商品名称或序号～")
                return "\n".join(lines)
            return "您想添加哪个商品到购物车呢？请告诉我商品名称。"

        # remove/update：列出购物车内容让用户选择
        if cart_items is None:
            cart_items = self._fetch_cart_items(user_id, jwt_token)
        if cart_items:
            verb = "删除" if action == "remove" else "修改数量"
            lines = [f"您想{verb}哪个商品呢？购物车里有："]
            for item in cart_items:
                lines.append(
                    f"{item['index']}. {item.get('title', '未知')} "
                    f"x{item['quantity']} ¥{item.get('price', 0)}"
                )
            lines.append("\n请告诉我商品名称或序号～")
            return "\n".join(lines)
        return "购物车是空的哦～"

    def _generate_cart_response(self, question: str, action: str,
                                 result: Dict[str, Any], context: str = "") -> str:
        """根据购物车操作结果生成自然语言回复"""
        if not result.get("success"):
            return result.get("message", "操作失败，请稍后再试。")

        message = result.get("message", "")

        if action == "list":
            data = result.get("data", {})
            items = data.get("items", [])
            if not items:
                return "购物车是空的，快去逛逛吧～"
            lines = ["购物车里的商品："]
            for item in items:
                lines.append(
                    f"{item['index']}. {item.get('title', '未知')} "
                    f"x{item['quantity']} ¥{item.get('price', 0)}"
                )
            return "\n".join(lines)

        return message

    def _execute_cart_action(self, action: str, user_id: str,
                             product_id: int = None, quantity: int = None,
                             jwt_token: str = None, conversation_id: str = None) -> Dict[str, Any]:
        """执行购物车操作并返回结果（通过 Java API）"""
        params = {"action": action, "user_id": user_id, "jwt_token": jwt_token}
        if product_id is not None:
            params["product_id"] = int(product_id)
        if quantity is not None:
            params["quantity"] = int(quantity)
        cart_tool = tool_registry.get_tool("cart_operation")
        if not cart_tool:
            return {"success": False, "message": "购物车工具未注册"}
        try:
            result = cart_tool.execute(params)
        except Exception as e:
            logger.error(f"[Cart] Tool execute failed: {e}", exc_info=True)
            return {"success": False, "message": f"操作异常: {e}"}
        logger.info(f"[Cart] Executed {action}: user_id={user_id}, product_id={product_id}, result={result}")
        # 操作成功后清除购物车缓存
        if result and result.get("success") and conversation_id:
            self._invalidate_cart_cache(conversation_id)
        return result if result else {"success": False, "message": "操作返回空结果"}

    def _build_confirm_message(self, action: str, product_id: int = None,
                               product_name: str = None) -> Dict[str, Any]:
        """构建确认提示消息，返回包含产品卡片和按钮的确认数据"""
        # 从数据库获取商品详情
        product_info = None
        if product_id:
            try:
                product_info = mysql_client.fetch_one(
                    "SELECT id, title, brand, base_price, image_url, rating, sub_category "
                    "FROM product WHERE id = %s",
                    (product_id,)
                )
            except Exception as e:
                logger.warning(f"[ShoppingAgent] Failed to fetch product info: {e}")

        # 确定操作描述
        action_desc = {
            "remove": "删除",
            "update": "修改数量",
            "clear": "清空购物车",
        }.get(action, action)

        # 构建确认卡片
        confirm_card = {
            "type": "confirm_card",
            "message": f"确定要{action_desc}吗？" if action == "clear" else f"确定要将「{product_name or '该商品'}」从购物车中{action_desc}吗？",
            "action": action,
            "product": None,
            "buttons": [
                {"type": "confirm", "label": "确认"},
                {"type": "cancel", "label": "取消"},
            ]
        }

        # 如果有商品信息，添加产品卡片
        if product_info:
            confirm_card["product"] = {
                "product_id": product_info.get("id"),
                "title": product_info.get("title", ""),
                "brand": product_info.get("brand", ""),
                "base_price": float(product_info.get("base_price", 0)),
                "image_url": product_info.get("image_url", ""),
                "rating": float(product_info.get("rating", 0)),
                "sub_category": product_info.get("sub_category", ""),
            }

        return confirm_card

    def _build_cart_selection(self, products: List[Dict[str, Any]]) -> Dict[str, Any]:
        """构建购物车选择卡片，让用户勾选要加入购物车的商品"""
        items = []
        for p in products:
            items.append({
                "product_id": p.get("id"),
                "title": p.get("title", ""),
                "brand": p.get("brand", ""),
                "base_price": float(p.get("base_price", 0)),
                "image_url": p.get("image_url", ""),
                "rating": float(p.get("rating", 0)) if p.get("rating") else 0,
            })
        return {
            "type": "cart_selection",
            "message": "请选择要加入购物车的商品：",
            "items": items,
        }

    def _build_cart_list_cards(self, user_id: str, cart_items: list = None, jwt_token: str = None) -> Dict[str, Any]:
        """构建购物车列表的商品卡片，用于查看购物车时展示"""
        if cart_items is None:
            cart_items = self._fetch_cart_items(user_id, jwt_token)
        items = []
        for item in cart_items:
            items.append({
                "product_id": item.get("product_id"),
                "title": item.get("title", ""),
                "brand": item.get("brand", ""),
                "base_price": float(item.get("price", 0)),
                "image_url": item.get("image_url", ""),
                "quantity": item.get("quantity", 1),
            })
        count = len(items)
        return {
            "type": "cart_list",
            "message": f"购物车里有 {count} 件商品：",
            "items": items,
        }

    def _detect_batch_remove(self, product_name: str, user_id: str, cart_items: list = None, jwt_token: str = None) -> Optional[List[int]]:
        """从 product_name 解析批量删除模式（前N个/后N个/最后N个），返回 product_id 列表"""
        import re
        # 匹配"前2个""后3个"格式
        m = re.search(r'(前|后)(\d+)[个件]', product_name or "")
        # 匹配"最后一个""最后两个"格式
        if not m:
            m2 = re.search(r'最后([一二三四五六七八九十\d]+)[个件]?', product_name or "")
            if m2:
                cn_map = {"一": 1, "两": 2, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}
                val = m2.group(1)
                count = cn_map.get(val)
                if count is None:
                    try:
                        count = int(val)
                    except ValueError:
                        return None
                if count <= 0:
                    return None
                # 当作"后N个"处理
                direction = "后"
            else:
                return None
        else:
            direction = m.group(1)
            count = int(m.group(2))
            if count <= 0:
                return None

        logger.info(f"[Cart] Batch detected from product_name: direction={direction}, count={count}")

        # 使用预取的购物车列表
        if cart_items is None:
            cart_items = self._fetch_cart_items(user_id, jwt_token)
        if not cart_items:
            return None

        if direction == "前":
            selected = cart_items[:count]
        else:
            selected = cart_items[-count:]

        # 返回 cart_item_id 列表（精确删除，避免重复商品时按 product_id 误删）
        return [item.get("id") for item in selected if item.get("id")]

    def _build_batch_confirm_message(self, cart_item_ids: List[int], user_id: str, cart_items: list = None) -> Dict[str, Any]:
        """构建批量删除的确认卡片，展示所有待删除商品"""
        count = len(cart_item_ids)

        # 从购物车列表中查找商品信息（避免重复查数据库）
        products = []
        if cart_items:
            id_set = set(cart_item_ids)
            for item in cart_items:
                if item.get("id") in id_set:
                    products.append({
                        "product_id": item.get("product_id"),
                        "title": item.get("title", ""),
                        "brand": item.get("brand", ""),
                        "base_price": float(item.get("price", 0)),
                        "image_url": item.get("image_url", ""),
                    })
        elif cart_item_ids:
            # fallback：从数据库查询
            product_ids = list(set(cart_item_ids))
            try:
                placeholders = ",".join(["%s"] * len(product_ids))
                rows = mysql_client.fetch_all(
                    f"SELECT id, title, brand, base_price, image_url, rating, sub_category "
                    f"FROM product WHERE id IN ({placeholders})",
                    tuple(product_ids)
                )
                for row in (rows or []):
                    products.append({
                        "product_id": row.get("id"),
                        "title": row.get("title", ""),
                        "brand": row.get("brand", ""),
                        "base_price": float(row.get("base_price", 0)),
                        "image_url": row.get("image_url", ""),
                    })
            except Exception as e:
                logger.warning(f"[ShoppingAgent] Failed to fetch products: {e}")

        confirm_card = {
            "type": "confirm_card",
            "message": f"确定要删除以下{count}件商品吗？",
            "action": "remove",
            "products": products,
            "buttons": [
                {"type": "confirm", "label": "确认删除"},
                {"type": "cancel", "label": "取消"},
            ]
        }

        return confirm_card

    def _handle_confirmation(self, question: str, pending: Dict,
                             user_id: str, conversation_id: str,
                             jwt_token: str = None) -> Dict[str, Any]:
        """处理用户确认/取消响应"""
        logger.info(f"[Cart] _handle_confirmation: question='{question}', user_id={user_id}, conv_id={conversation_id}, pending={pending}")
        lower_q = question.strip().lower()

        # 判断是否确认
        confirm_keywords = ["确认", "确定", "是的", "好的", "好", "对", "yes", "ok", "执行", "删吧", "清吧"]
        cancel_keywords = ["取消", "不要", "算了", "不", "否", "no", "cancel"]

        is_confirm = any(kw in lower_q for kw in confirm_keywords)
        is_cancel = any(kw in lower_q for kw in cancel_keywords)

        if is_cancel:
            ShoppingAgent._pending_cart_actions.pop(conversation_id, None)
            return {
                "answer": "好的，已取消操作。",
                "sources": [], "has_sources": False,
                "task_type": "cart", "product_cards": []
            }

        if is_confirm:
            # 执行待确认的操作
            action = pending["action"]
            ShoppingAgent._pending_cart_actions.pop(conversation_id, None)

            try:
                # 优先从参数获取 jwt_token，其次从 pending 中获取
                token = jwt_token or pending.get("jwt_token")
                # 批量删除（使用 cart_item_ids 精确删除）
                batch_cart_item_ids = pending.get("cart_item_ids")
                if batch_cart_item_ids:
                    cart_tool = tool_registry.get_tool("cart_operation")
                    success_count = 0
                    for cid in batch_cart_item_ids:
                        logger.info(f"[Cart] Batch remove by cart_item_id: {cid}, user_id={user_id}")
                        if cart_tool:
                            result = cart_tool.execute({"action": "remove", "cart_item_id": cid, "user_id": user_id, "jwt_token": token})
                        else:
                            result = {"success": False, "message": "购物车工具未注册"}
                        logger.info(f"[Cart] Batch remove result: cid={cid}, success={result.get('success')}, msg={result.get('message')}")
                        if result.get("success"):
                            success_count += 1
                    answer = f"已成功删除 {success_count} 件商品。"
                else:
                    cid = pending.get("cart_item_id")
                    pid = pending.get("product_id")
                    qty = pending.get("quantity") or 1  # 默认删1个
                    if cid:
                        # 按购物车项ID精确删除（用于"删除第N个"场景）
                        logger.info(f"[Cart] Remove by cart_item_id: {cid}, user_id={user_id}")
                        cart_tool = tool_registry.get_tool("cart_operation")
                        result = cart_tool.execute({"action": "remove", "cart_item_id": cid, "user_id": user_id, "jwt_token": token}) if cart_tool else {"success": False}
                    else:
                        logger.info(f"[Cart] Single remove: pid={pid}, qty={qty}, user_id={user_id}")
                        result = self._execute_cart_action(action, user_id, pid, qty, token, conversation_id)
                    logger.info(f"[Cart] Single remove result: success={result.get('success')}, msg={result.get('message')}")
                    answer = self._generate_cart_response(question, action, result, "")
            except Exception as e:
                logger.error(f"[Cart] Confirmation execution failed: {e}", exc_info=True)
                answer = "操作执行失败，请稍后再试。"
            return {
                "answer": answer,
                "sources": [], "has_sources": False,
                "task_type": "cart", "product_cards": []
            }

        # 无法识别的回复，重新询问
        action = pending.get("action", "操作")
        return {
            "answer": f"请回复「确认」执行{action}，或「取消」放弃操作。",
            "sources": [], "has_sources": False,
            "task_type": "cart", "product_cards": []
        }

    def _search_products(self, question: str, user_profile: str = "", conversation_context: str = "") -> List[Dict[str, Any]]:
        """从 MySQL 搜索匹配商品。

        搜索流程：
        1. 提取排除词（LLM 识别"不要/除了"等否定语义）
        2. 提取搜索关键词（LLM + 正则 fallback + 品牌/品类匹配）
        3. 同义词展开 + 精确匹配三级优先级
        4. SQL 模糊搜索 + 品类过滤
        5. 用户画像偏好排序
        6. 后处理（去重 + 加权排序）

        Args:
            question: 用户问题
            user_profile: 用户画像字符串（包含偏好标签、肤质等）
            conversation_context: 对话上下文（帮助理解承接上文的查询）

        Returns:
            匹配的商品列表，每个元素包含 id/title/brand/base_price 等字段
        """
        try:
            # Step 1: 提取排除关键词（如"不要含酒精的" → ["酒精"]）
            exclusions = self._extract_exclusion_terms(question)

            # Step 2: 提取搜索关键词（传入对话上下文，让 LLM 理解承接上文的查询）
            keywords = self._extract_keywords(question, conversation_context)

            # 如果关键词为空，尝试用数据库品牌/品类匹配
            if not keywords:
                keywords = self._match_brands_and_categories(question)

            if not keywords:
                # 无关键词时返回热门商品
                results = mysql_client.fetch_all(
                    "SELECT id, title, brand, base_price, image_url, rating, "
                    "review_count, sales_count, tags, sub_category, 0 AS relevance_score "
                    "FROM product WHERE status = 1 ORDER BY sales_count DESC LIMIT 15"
                )
                return self._post_process_products(self._filter_exclusions(results, exclusions))

            # LLM 已直接返回搜索词（含同义词展开），只需去重和过滤
            search_terms = list(dict.fromkeys(keywords))  # 保序去重
            search_terms = [t for t in search_terms if len(t) >= 2]

            # 用户画像偏好补充：当查询较短（≤2个搜索词）时，用偏好关键词补充
            if user_profile:
                import re
                pref_match = re.search(r'偏好[：:]\s*([^；;]+)', user_profile)
                if pref_match:
                    prefs = [tag.strip() for tag in re.split(r'[、,，]', pref_match.group(1)) if tag.strip()]
                    if prefs and len(search_terms) <= 2:
                        for pref in prefs:
                            if pref not in search_terms and len(pref) >= 2:
                                search_terms.append(pref)

            logger.info(f"[ShoppingAgent] Search terms: {search_terms}")

            # 用 LIKE 模糊匹配（搜索 title + brand + tags + sub_category + description）
            # 相关性评分：title 命中权重最高(3分)，brand/tags/sub_category 次之(2分)，description 最低(1分)
            relevance_parts = []
            conditions = " OR ".join([
                "(title LIKE %s OR brand LIKE %s OR tags LIKE %s OR sub_category LIKE %s OR description LIKE %s)"
                for _ in search_terms
            ])
            params = []
            for kw in search_terms:
                like_val = f"%{kw}%"
                # WHERE 子句参数（5个）
                params.extend([like_val, like_val, like_val, like_val, like_val])
                # CASE 表达式参数（5个），参数化防注入
                relevance_parts.append(
                    "(CASE WHEN title LIKE %s THEN 3 ELSE 0 END + "
                    "CASE WHEN brand LIKE %s THEN 2 ELSE 0 END + "
                    "CASE WHEN tags LIKE %s THEN 2 ELSE 0 END + "
                    "CASE WHEN sub_category LIKE %s THEN 2 ELSE 0 END + "
                    "CASE WHEN description LIKE %s THEN 1 ELSE 0 END)"
                )
                params.extend([like_val, like_val, like_val, like_val, like_val])

            relevance_expr = " + ".join(relevance_parts)

            sql = (
                f"SELECT id, title, brand, base_price, image_url, rating, "
                f"review_count, sales_count, tags, sub_category, "
                f"({relevance_expr}) AS relevance_score "
                f"FROM product WHERE status = 1 AND ({conditions}) "
                f"ORDER BY relevance_score DESC, sales_count DESC LIMIT 15"
            )
            results = mysql_client.fetch_all(sql, tuple(params))
            logger.info(f"[ShoppingAgent] SQL returned {len(results)} results for terms {search_terms}")

            # 品类过滤：检查搜索词是否直接命中结果中的 sub_category
            # 例如搜索"卫衣"时，结果中 sub_category="卫衣"的商品优先，排除"跑步鞋"等
            if results:
                result_categories = {r.get("sub_category") for r in results if r.get("sub_category")}
                matched_categories = {kw for kw in search_terms if kw in result_categories}
                if matched_categories:
                    cat_filtered = [r for r in results if r.get("sub_category") in matched_categories]
                    if cat_filtered and len(cat_filtered) < len(results):
                        logger.info(f"[ShoppingAgent] Sub-category filter: {len(results)} → {len(cat_filtered)} (matched: {matched_categories})")
                        results = cat_filtered

            # 排除过滤后无结果时，重新用 LLM 从对话上下文中提取品类关键词再搜一次
            if not results:
                logger.info(f"[ShoppingAgent] No match for keywords {keywords}, retrying with context")
                fallback_keywords = self._extract_keywords_with_llm(question, conversation_context)
                # 过滤单字
                fallback_keywords = [t for t in fallback_keywords if len(t) >= 2]
                if fallback_keywords and fallback_keywords != keywords:
                    fb_relevance_parts = []
                    fb_conditions = " OR ".join([
                        "(title LIKE %s OR brand LIKE %s OR tags LIKE %s OR sub_category LIKE %s OR description LIKE %s)"
                        for _ in fallback_keywords
                    ])
                    fb_params = []
                    for kw in fallback_keywords:
                        like_val = f"%{kw}%"
                        fb_params.extend([like_val, like_val, like_val, like_val, like_val])
                        fb_relevance_parts.append(
                            f"(CASE WHEN title LIKE '{like_val}' THEN 3 ELSE 0 END + "
                            f"CASE WHEN brand LIKE '{like_val}' THEN 2 ELSE 0 END + "
                            f"CASE WHEN tags LIKE '{like_val}' THEN 2 ELSE 0 END + "
                            f"CASE WHEN sub_category LIKE '{like_val}' THEN 2 ELSE 0 END + "
                            f"CASE WHEN description LIKE '{like_val}' THEN 1 ELSE 0 END)"
                        )
                    fb_relevance_expr = " + ".join(fb_relevance_parts)
                    results = mysql_client.fetch_all(
                        f"SELECT id, title, brand, base_price, image_url, rating, "
                        f"review_count, sales_count, tags, sub_category, "
                        f"({fb_relevance_expr}) AS relevance_score "
                        f"FROM product WHERE status = 1 AND ({fb_conditions}) "
                        f"ORDER BY relevance_score DESC, sales_count DESC LIMIT 15",
                        tuple(fb_params)
                    )
                    # fallback 结果也做品类过滤
                    if results:
                        result_cats = {r.get("sub_category") for r in results if r.get("sub_category")}
                        matched_cats = {kw for kw in fallback_keywords if kw in result_cats}
                        if matched_cats:
                            fb_cat_filtered = [r for r in results if r.get("sub_category") in matched_cats]
                            if fb_cat_filtered and len(fb_cat_filtered) < len(results):
                                logger.info(f"[ShoppingAgent] Fallback category filter: {len(results)} → {len(fb_cat_filtered)} (matched: {matched_cats})")
                                results = fb_cat_filtered
                    logger.info(f"[ShoppingAgent] Fallback with context returned {len(results)} products")

            # 最终兜底：正则分词（LLM 关键词都搜不到时）
            if not results:
                regex_keywords = self._extract_keywords_regex(question)
                regex_keywords = [t for t in regex_keywords if len(t) >= 2]
                if regex_keywords and regex_keywords != keywords:
                    logger.info(f"[ShoppingAgent] Retrying with regex keywords: {regex_keywords}")
                    rx_conditions = " OR ".join([
                        "(title LIKE %s OR brand LIKE %s OR tags LIKE %s OR sub_category LIKE %s)"
                        for _ in regex_keywords
                    ])
                    rx_params = []
                    for kw in regex_keywords:
                        like_val = f"%{kw}%"
                        rx_params.extend([like_val, like_val, like_val, like_val])
                    results = mysql_client.fetch_all(
                        f"SELECT id, title, brand, base_price, image_url, rating, "
                        f"review_count, sales_count, tags, sub_category, 0 AS relevance_score "
                        f"FROM product WHERE status = 1 AND ({rx_conditions}) "
                        f"ORDER BY sales_count DESC LIMIT 15",
                        tuple(rx_params)
                    )
                    logger.info(f"[ShoppingAgent] Regex fallback returned {len(results)} products")

            if not results:
                logger.info(f"[ShoppingAgent] Still no match, returning popular products")
                results = mysql_client.fetch_all(
                    "SELECT id, title, brand, base_price, image_url, rating, "
                    "review_count, sales_count, tags, sub_category, 0 AS relevance_score "
                    "FROM product WHERE status = 1 ORDER BY sales_count DESC LIMIT 15"
                )

            final = self._post_process_products(self._filter_exclusions(results, exclusions))
            logger.info(f"[ShoppingAgent] After post-processing: {len(final)} products")
            return final
        except Exception as e:
            logger.error(f"[ShoppingAgent] Product search error: {e}")
            return []

    def _extract_keywords(self, question: str, conversation_context: str = "") -> List[str]:
        """从用户问题中提取搜索关键词 - 优先用LLM，fallback到正则"""
        # 优先用 LLM 提取关键词
        llm_keywords = self._extract_keywords_with_llm(question, conversation_context)
        if llm_keywords:
            return llm_keywords

        # Fallback: 正则分词
        return self._extract_keywords_regex(question)

    def _extract_keywords_with_llm(self, question: str, conversation_context: str = "") -> List[str]:
        """用 LLM 从用户问题中提取商品搜索关键词，支持对话上下文理解"""
        try:
            if not self.llm_service.llm:
                return []

            context_hint = ""
            if conversation_context:
                context_hint = f"\n对话历史（用于理解上下文）：\n{conversation_context}\n"

            prompt = (
                f"从以下用户问题中提取商品搜索词，用于 MySQL LIKE 搜索。\n"
                f"要求：\n"
                f"1. 返回搜索词列表，用逗号分隔，每个词至少2个中文字符\n"
                f"2. 包含：品牌名（完整）、产品类型、功效/成分\n"
                f"3. 品类泛词要展开为具体子品类（如\"衣服\"→\"卫衣,T恤,短袖\"；\"鞋子\"→\"篮球鞋,跑步鞋,徒步鞋\"）\n"
                f"4. 品牌名保持完整，不要拆分（如\"元气森林\"不要拆成\"元气\"或\"森林\"）\n"
                f"5. 不要生成与商品无关的通用词（如\"推荐\"、\"相似\"、\"图片\"）\n"
                f"6. 如果用户说排除/不要某品牌，只提取品类词\n"
                f"{context_hint}\n"
                f"用户问题：{question}\n\n"
                f"搜索词："
            )
            result = self.llm_service.llm.invoke(prompt)
            text = result.content if hasattr(result, 'content') else str(result)
            # 解析逗号分隔的关键词
            keywords = [kw.strip() for kw in text.strip().split(',') if kw.strip()]
            logger.info(f"[ShoppingAgent] LLM extracted keywords: {keywords}")
            return keywords[:8]
        except Exception as e:
            logger.warning(f"[ShoppingAgent] LLM keyword extraction failed: {e}")
            return []

    def _extract_exclusion_terms(self, question: str) -> List[str]:
        """用 LLM 从用户问题中提取排除关键词，如"不要优衣库" → ["优衣库"]"""
        try:
            if not self.llm_service.llm:
                return []
            prompt = (
                "从用户问题中提取用户明确「不要/排除/除了」的关键词。\n"
                "注意：用户想要的商品不是排除词！只有用户明确说「不要」「除了」「不要xx品牌」的才算排除词。\n\n"
                "示例：\n"
                "问题：推荐一款手机，不要苹果 → 排除词：苹果\n"
                "问题：有什么好用的耳机，除了索尼 → 排除词：索尼\n"
                "问题：一部Apple iPhone 17 Pro Max手机 → 排除词：（无）\n"
                "问题：推荐耳机，不要贵的 → 排除词：（无，贵是价格偏好不是排除词）\n\n"
                "只返回排除关键词，用逗号分隔。如果没有排除意图，返回空字符串。\n\n"
                f"用户问题：{question}\n\n"
                "排除关键词："
            )
            result = self.llm_service.llm.invoke(prompt)
            text = result.content if hasattr(result, 'content') else str(result)
            text = text.strip().strip('"').strip("'")
            # 过滤掉明显的误判
            if not text or text in ["无", "（无）", "无排除词", "空字符串", "没有"]:
                return []
            exclusions = [kw.strip() for kw in text.split(',') if kw.strip() and len(kw.strip()) >= 2]
            logger.info(f"[ShoppingAgent] LLM extracted exclusions: {exclusions}")
            return exclusions
        except Exception as e:
            logger.warning(f"[ShoppingAgent] LLM exclusion extraction failed: {e}")
            return []

    def _filter_exclusions(self, products: List[Dict[str, Any]], exclusions: List[str]) -> List[Dict[str, Any]]:
        """过滤掉包含排除关键词的商品"""
        if not exclusions or not products:
            return products
        filtered = []
        for p in products:
            text = f"{p.get('title', '')} {p.get('tags', '')} {p.get('sub_category', '')}".lower()
            if not any(excl.lower() in text for excl in exclusions):
                filtered.append(p)
        return filtered

    def _extract_keywords_regex(self, question: str) -> List[str]:
        """正则分词提取关键词（LLM不可用时的fallback）"""
        import re

        stop_words = {
            "推荐", "一款", "一下", "一些", "有没有", "帮我", "我想",
            "请问", "什么", "比较", "适合", "好的", "可以", "怎么样", "如何",
            "的", "了", "吗", "呢", "吧", "啊", "哦", "嗯",
            "和", "与", "或", "但", "是", "有", "在",
            "更", "最", "很", "非常", "特别", "便宜", "贵", "好", "差",
            "多少钱", "价格", "性价比", "想要", "想", "要",
            "图片", "内容", "相似", "商品",
        }

        # 提取英文单词
        en_words = re.findall(r'[a-zA-Z0-9]+', question)
        # 提取中文片段（2字及以上）
        clean = re.sub(r'[，。！？、；：""''【】《》（）\(\)\[\]\{\}<>\?\!\.\,\;\:\"\'\-\—\…\~\`\s]', ' ', question)
        cn_segments = re.findall(r'[一-鿿]{2,}', clean)
        cn_words = [seg for seg in cn_segments if seg not in stop_words]

        keywords = [w for w in cn_words + en_words if w.lower() not in stop_words and len(w) >= 2]
        seen = set()
        result = []
        for kw in keywords:
            if kw not in seen:
                seen.add(kw)
                result.append(kw)
        return result[:8]

    def _match_brands_and_categories(self, question: str) -> List[str]:
        """用数据库中的品牌和品类匹配用户问题"""
        try:
            # 获取所有品牌
            brands = mysql_client.fetch_all(
                "SELECT DISTINCT brand FROM product WHERE status = 1 AND brand IS NOT NULL"
            )
            # 获取所有品类
            categories = mysql_client.fetch_all(
                "SELECT DISTINCT sub_category FROM product WHERE status = 1 AND sub_category IS NOT NULL"
            )

            matched = []
            for b in brands:
                brand = b.get('brand', '')
                if brand and brand in question:
                    matched.append(brand)
            for c in categories:
                cat = c.get('sub_category', '')
                if cat and cat in question:
                    matched.append(cat)

            return matched[:5]
        except Exception as e:
            logger.error(f"[ShoppingAgent] Brand/category match error: {e}")
            return []

    def _post_process_products(self, products: List[Dict[str, Any]], limit: int = 5) -> List[Dict[str, Any]]:
        """检索结果后处理：去重、过滤、综合排序"""
        if not products:
            logger.info("[PostProcess] No products to process")
            return []
        logger.info(f"[PostProcess] Input: {len(products)} products")

        # 1. 去重：按 product id 去重，保留首次出现的
        seen_ids = set()
        deduped = []
        for p in products:
            pid = p.get("id")
            if pid and pid not in seen_ids:
                seen_ids.add(pid)
                deduped.append(p)

        # 2. 过滤：确保 status == 1（防御性检查，SQL 已过滤）
        filtered = [p for p in deduped if p.get("status", 1) == 1]

        # 3. 综合排序：相关性权重 0.5 + 评分权重 0.2 + 销量权重 0.3
        # 先计算各维度最大值用于归一化（MySQL返回Decimal，需转float）
        max_relevance = float(max((p.get("relevance_score", 0) or 0) for p in filtered)) or 1
        max_rating = float(max((p.get("rating", 0) or 0) for p in filtered)) or 1
        max_sales = float(max((p.get("sales_count", 0) or 0) for p in filtered)) or 1

        for p in filtered:
            relevance_norm = float(p.get("relevance_score", 0) or 0) / max_relevance
            rating_score = float(p.get("rating", 0) or 0) / max_rating
            sales_score = float(p.get("sales_count", 0) or 0) / max_sales
            p["_sort_score"] = relevance_norm * 0.5 + rating_score * 0.2 + sales_score * 0.3

        filtered.sort(key=lambda x: x.get("_sort_score", 0), reverse=True)

        # 移除临时排序字段并返回 top N
        for p in filtered:
            p.pop("_sort_score", None)

        return filtered[:limit]

    def _build_product_context(self, products: List[Dict[str, Any]]) -> str:
        """构建商品信息上下文，供 LLM 生成推荐话术。
        使用结构化标签格式，便于 LLM 精确引用，减少编造。"""
        if not products:
            return ""
        lines = ["以下是匹配到的商品信息（请严格基于此数据推荐，不要编造任何信息）："]
        for i, p in enumerate(products, 1):
            lines.append(
                f"[商品{i}] 标题：{p.get('title', '未知')} | "
                f"品牌：{p.get('brand', '无')} | "
                f"价格：¥{p.get('base_price', 0)} | "
                f"评分：{p.get('rating', 0)} | "
                f"销量：{p.get('sales_count', 0)} | "
                f"标签：{p.get('tags', '')}"
            )
        return "\n".join(lines)

    def _generate_recommendation(self, question: str, context: str,
                                  products: List[Dict[str, Any]], user_profile: str = "") -> str:
        """用 LLM 生成推荐话术"""
        if not products:
            return "抱歉，暂时没有找到完全匹配您需求的商品，换个关键词试试吧～"

        season = self._get_current_season()
        # 判断是否为图片识别结果（通常以量词+品牌+品类开头，如"一副xxx耳机"）
        is_image_query = any(question.startswith(w) for w in ["一副", "一个", "一双", "一件", "一台", "一部", "一条", "一瓶", "一盒", "一罐"])
        image_hint = ""
        if is_image_query:
            image_hint = (
                f"\n9. 用户通过拍照识别了商品，这是识别结果。请推荐商城中同类商品（同品类/同类型），"
                f"不要推荐完全不同的品类（如用户拍的是耳机，不要推荐手机或电脑）\n"
                f"10. 开头可以说「看到你在找xxx」之类的话，自然地衔接推荐\n"
            )

        prompt = (
            f"你是智能导购助手「小智」。根据用户需求和商品信息，给出简短推荐。\n\n"
            f"当前季节：{season}\n"
            f"用户画像：{user_profile or '（未知）'}\n"
            f"用户问题：{question}\n\n"
            f"商品信息：\n{context}\n\n"
            f"回复规则（严格遵守）：\n"
            f"1. 控制在80字以内，简洁明了\n"
            f"2. 根据用户性别调整称呼：男性用「兄弟/哥们」，女性用「姐妹/小姐姐」\n"
            f"3. 不要用与用户性别不符的称呼\n"
            f"4. 直接推荐2-3款，说明核心卖点即可\n"
            f"5. 【重要】不要编造商品不存在的功能、参数或特性\n"
            f"6. 【重要】不要编造价格、评分、销量等数据，所有数据必须来自商品信息\n"
            f"7. 【重要】只推荐商品信息中列出的商品，不要推荐不存在的商品\n"
            f"8. 如果用户有肤质信息，推荐护肤品时说明是否适合该肤质\n"
            f"9. 如果用户有偏好标签，优先推荐与偏好相关的商品\n"
            f"10. 结合当前季节推荐应季商品，如夏季推荐防晒/清爽类，冬季推荐保湿/保暖类\n"
            f"{image_hint}"
        )
        try:
            result = self.llm_service.llm.invoke(prompt).content
            # 防幻觉后处理：校验 LLM 回复中提到的价格是否与商品数据一致
            result = self._validate_llm_prices(result, products)
            return result
        except Exception as e:
            logger.error(f"[ShoppingAgent] LLM generation error: {e}")
            # fallback: 简单拼接推荐
            titles = [p.get('title', '') for p in products[:3]]
            return f"为您推荐：{'、'.join(titles)}，这几款都很受欢迎哦！"

    def _validate_llm_prices(self, text: str, products: List[Dict[str, Any]]) -> str:
        """防幻觉后处理：校验 LLM 回复中提到的价格，与商品数据不一致时替换为正确价格"""
        import re
        if not products:
            return text
        # 构建商品标题→价格映射
        price_map = {}
        for p in products:
            title = p.get('title', '')
            price = p.get('base_price', 0)
            if title and price:
                # 取标题前6个字作为模糊匹配键
                price_map[title[:6]] = float(price)

        # 查找回复中所有 ¥xxx 或 xxx元 的价格
        price_pattern = re.compile(r'¥(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)元')
        for match in price_pattern.finditer(text):
            mentioned_price = float(match.group(1) or match.group(2))
            # 检查该价格附近是否有商品名
            context_start = max(0, match.start() - 20)
            context_text = text[context_start:match.end()]
            for key, correct_price in price_map.items():
                if key in context_text and abs(mentioned_price - correct_price) > 0.1:
                    # 价格不一致，替换
                    old_str = match.group(0)
                    new_str = f"¥{correct_price}" if match.group(1) else f"{correct_price}元"
                    text = text.replace(old_str, new_str, 1)
                    logger.warning(f"[ShoppingAgent] 幻觉检测：价格不一致 '{old_str}' → '{new_str}'（商品：{key}）")
                    break
        return text

    def _build_product_cards(self, products: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """构建商品卡片数据，供 App 端展示"""
        cards = []
        for p in products[:5]:
            cards.append({
                "product_id": p.get("id"),
                "title": p.get("title", ""),
                "brand": p.get("brand", ""),
                "price": float(p.get("base_price", 0)),
                "image_url": p.get("image_url", ""),
                "rating": float(p.get("rating", 0)),
                "sales_count": p.get("sales_count", 0),
                "sub_category": p.get("sub_category", ""),
                "reason": ""  # 由前端展示折叠的推荐理由
            })
        return cards

    def _build_sources(self, docs: list) -> list:
        """构建引用来源"""
        seen_doc_ids = set()
        sources = []
        for doc in docs:
            metadata = getattr(doc, 'metadata', {}) if hasattr(doc, 'metadata') else (
                doc.get('metadata', {}) if isinstance(doc, dict) else {}
            )
            doc_id = metadata.get("doc_id")
            if doc_id and doc_id in seen_doc_ids:
                continue
            if doc_id:
                seen_doc_ids.add(doc_id)
            sources.append({
                "doc_id": doc_id,
                "doc": metadata.get("source", "未知文档"),
                "page": metadata.get("page"),
                "chunk_index": metadata.get("chunk_index"),
                "score": metadata.get("score", 0)
            })
        return sources
