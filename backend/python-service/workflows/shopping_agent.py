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

            # 3.1 批量删除检测
            batch_ids = None
            if action == "remove" and product_id is None:
                batch_ids = self._detect_batch_remove(product_name, user_id, cart_items, jwt_token)
                if batch_ids:
                    logger.info(f"[Cart] Batch remove detected: {len(batch_ids)} items")

            # 3.2 remove/update 需要 product_id，缺失时询问用户（clear/list/批量不需要）
            if action not in ("list", "clear") and product_id is None and not batch_ids:
                clarification = self._ask_which_product(action, question, user_id, context, conversation_id, cart_items, jwt_token)
                return {
                    "answer": clarification,
                    "sources": [], "has_sources": False,
                    "task_type": "cart", "product_cards": []
                }

            # 4. 删除/修改/清空需要确认（返回确认卡片）
            if action in ("remove", "update", "clear"):
                if batch_ids:
                    ShoppingAgent._pending_cart_actions[conversation_id] = {
                        "action": action, "product_ids": batch_ids,
                        "user_id": user_id, "jwt_token": jwt_token
                    }
                    confirm_card = self._build_batch_confirm_message(batch_ids, user_id)
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
        """处理购物车操作请求（流式）"""
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

            # 3.1 批量删除检测：处理"删除前三个/删除后两个/删除第2到第4个"
            batch_ids = None
            if action == "remove" and product_id is None:
                batch_ids = self._detect_batch_remove(product_name, user_id, cart_items, jwt_token)
                if batch_ids:
                    logger.info(f"[Cart Stream] Batch remove detected: {len(batch_ids)} items")

            # 3.2 缺失 product_id 时询问用户（clear/list/批量不需要）
            if action not in ("list", "clear") and product_id is None and not batch_ids:
                clarification = self._ask_which_product(action, question, user_id, context, conversation_id, cart_items, jwt_token)
                yield json.dumps({"type": "token", "content": clarification})
                yield json.dumps({"type": "end"})
                return

            # 4. 删除/修改/清空需要确认（返回确认卡片）
            if action in ("remove", "update", "clear"):
                if batch_ids:
                    # 批量删除：存入多个 product_id
                    ShoppingAgent._pending_cart_actions[conversation_id] = {
                        "action": action, "product_ids": batch_ids,
                        "user_id": user_id, "jwt_token": jwt_token
                    }
                    logger.info(f"[Cart] Stored pending batch remove: conv_id={conversation_id}, batch_ids={batch_ids}, user_id={user_id}")
                    confirm_card = self._build_batch_confirm_message(batch_ids, user_id)
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
        """用 LLM 解析购物车子意图，返回 (action, product_id, quantity, product_name)"""
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
            "- 如果用户说\"第一个/第二个/第N个\"，根据购物车列表中的序号确定对应商品\n"
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

        return [item["product_id"] for item in selected]

    def _build_batch_confirm_message(self, product_ids: List[int], user_id: str) -> Dict[str, Any]:
        """构建批量删除的确认卡片，展示所有待删除商品"""
        count = len(product_ids)

        # 获取所有商品信息
        products = []
        if product_ids:
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
                        "rating": float(row.get("rating", 0)),
                        "sub_category": row.get("sub_category", ""),
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
                # 批量删除
                batch_ids = pending.get("product_ids")
                if batch_ids:
                    success_count = 0
                    for pid in batch_ids:
                        logger.info(f"[Cart] Batch remove: pid={pid}, user_id={user_id}")
                        result = self._execute_cart_action(action, user_id, pid, 1, token, conversation_id)
                        logger.info(f"[Cart] Batch remove result: pid={pid}, success={result.get('success')}, msg={result.get('message')}")
                        if result.get("success"):
                            success_count += 1
                    answer = f"已成功删除 {success_count} 件商品。"
                else:
                    pid = pending.get("product_id")
                    qty = pending.get("quantity") or 1  # 默认删1个
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
        """从 MySQL 搜索匹配商品"""
        try:
            # 提取排除关键词（如"不要含酒精的" → ["酒精"]）
            exclusions = self._extract_exclusion_terms(question)

            # 提取关键词进行商品搜索（传入对话上下文，让 LLM 理解承接上文的查询）
            keywords = self._extract_keywords(question, conversation_context)

            # 如果关键词为空，尝试用数据库品牌/品类匹配
            if not keywords:
                keywords = self._match_brands_and_categories(question)

            if not keywords:
                # 无关键词时返回热门商品
                results = mysql_client.fetch_all(
                    "SELECT id, title, brand, base_price, image_url, rating, "
                    "review_count, sales_count, tags, sub_category "
                    "FROM product WHERE status = 1 ORDER BY sales_count DESC LIMIT 15"
                )
                return self._post_process_products(self._filter_exclusions(results, exclusions))

            # 同义词映射：用户常用泛称 → 数据库中的具体品类/关键词
            SYNONYM_MAP = {
                "衣服": ["卫衣", "T恤", "短袖", "速干"],
                "裤子": ["户外裤", "瑜伽裤", "运动短裤", "运动长裤"],
                "鞋": ["篮球鞋", "跑步鞋", "徒步鞋", "运动鞋"],
                "鞋子": ["篮球鞋", "跑步鞋", "徒步鞋", "运动鞋", "鞋"],
                "运动鞋": ["篮球鞋", "跑步鞋", "徒步鞋"],
                "护肤品": ["精华", "面霜", "化妆水", "面膜", "眼霜", "防晒", "洁面"],
                "护肤": ["精华", "面霜", "化妆水", "面膜", "眼霜", "防晒"],
                "水乳": ["化妆水", "面霜", "精华"],
                "彩妆": ["粉底液", "蜜粉", "唇釉", "眉笔"],
                "化妆品": ["粉底液", "蜜粉", "唇釉", "眉笔", "卸妆"],
                "数码": ["智能手机", "笔记本电脑", "平板电脑", "真无线耳机"],
                "零食": ["坚果", "方便食品"],
                "饮料": ["功能饮料", "碳酸饮料", "茶饮", "牛奶", "咖啡"],
                "运动装备": ["运动短裤", "运动长裤", "速干T恤", "瑜伽裤"],
                "户外装备": ["徒步鞋", "户外裤", "背包"],
            }

            # 从用户画像中提取偏好关键词，用于补充搜索
            profile_preferences = []
            if user_profile:
                import re
                # 提取"偏好：运动、数码"中的标签
                pref_match = re.search(r'偏好[：:]\s*([^；;]+)', user_profile)
                if pref_match:
                    profile_preferences = [tag.strip() for tag in re.split(r'[、,，]', pref_match.group(1)) if tag.strip()]
                # 提取"肤质：油性"用于护肤品场景
                skin_match = re.search(r'肤质[：:]\s*([^；;]+)', user_profile)
                if skin_match:
                    profile_preferences.append(skin_match.group(1).strip())

            # 精确匹配：用户说具体品类时，只匹配对应数据库品类，不展开
            # 解决"推荐跑鞋"误匹配"篮球鞋"的问题
            PRECISE_MATCH_MAP = {
                "跑鞋": ["跑步鞋"],
                "跑步鞋": ["跑步鞋"],
                "跑步": ["跑步鞋"],
                "篮球鞋": ["篮球鞋"],
                "篮球": ["篮球鞋"],
                "徒步鞋": ["徒步鞋"],
                "徒步": ["徒步鞋"],
                "咖啡": ["咖啡"],
                "茶": ["茶饮"],
                "茶饮": ["茶饮"],
                "牛奶": ["牛奶"],
                "碳酸": ["碳酸饮料"],
                "面膜": ["面膜"],
                "精华": ["精华"],
                "防晒": ["防晒霜", "防晒"],
                "眼霜": ["眼霜"],
                "面霜": ["面霜"],
                "洁面": ["洁面"],
                "卸妆": ["卸妆"],
                "坚果": ["坚果"],
                "手机": ["智能手机"],
                "笔记本": ["笔记本电脑"],
                "平板": ["平板电脑"],
                "耳机": ["真无线耳机"],
                "背包": ["背包"],
            }

            # 泛词集合：这些词需要展开子品类
            BROAD_KEYWORDS = {"衣服", "裤子", "鞋", "鞋子", "运动鞋", "护肤品", "护肤",
                              "水乳", "彩妆", "化妆品", "数码", "零食", "饮料",
                              "运动装备", "户外装备"}

            import re
            search_terms = []
            for kw in keywords:
                # 优先级1：精确匹配（"跑鞋" → 只匹配"跑步鞋"，不展开）
                if kw in PRECISE_MATCH_MAP:
                    for term in PRECISE_MATCH_MAP[kw]:
                        if term not in search_terms:
                            search_terms.append(term)
                    continue  # 精确命中后跳过泛词展开和拆字逻辑

                # 优先级2：泛词展开（"鞋子" → "篮球鞋","跑步鞋","徒步鞋","运动鞋"）
                if kw in BROAD_KEYWORDS and kw in SYNONYM_MAP:
                    if kw not in search_terms:
                        search_terms.append(kw)
                    for syn in SYNONYM_MAP[kw]:
                        if syn not in search_terms:
                            search_terms.append(syn)
                    continue  # 泛词展开后跳过拆字逻辑

                # 优先级3：普通关键词 + 拆字匹配
                if kw not in search_terms:
                    search_terms.append(kw)

                cn_chars = re.findall(r'[一-鿿]', kw)
                # 2字中文词：拆出首字 + 去掉"子/品/物"后缀的词根
                if len(cn_chars) == 2:
                    first_char = cn_chars[0]
                    if first_char not in search_terms and len(first_char) >= 1:
                        search_terms.append(first_char)
                    # "鞋子"→"鞋"，"裤子"→"裤"，"杯子"→"杯"
                    if cn_chars[1] in ('子', '品', '物'):
                        root = cn_chars[0]
                        if root not in search_terms:
                            search_terms.append(root)
                # 3字以上拆出所有2字子串
                if len(cn_chars) >= 3:
                    for i in range(len(cn_chars) - 1):
                        sub = ''.join(cn_chars[i:i+2])
                        if sub not in search_terms:
                            search_terms.append(sub)

            # 用户画像偏好补充：当查询较短（≤2个搜索词）时，用偏好关键词补充
            # 避免"推荐护肤品"返回与用户肤质/偏好无关的商品
            if profile_preferences and len(search_terms) <= 2:
                for pref in profile_preferences:
                    if pref not in search_terms and len(pref) >= 2:
                        search_terms.append(pref)

            logger.info(f"[ShoppingAgent] Search terms: {search_terms}")

            # 用 LIKE 模糊匹配
            conditions = " OR ".join([
                "(title LIKE %s OR brand LIKE %s OR tags LIKE %s OR sub_category LIKE %s)"
                for _ in search_terms
            ])
            params = []
            for kw in search_terms:
                like_val = f"%{kw}%"
                params.extend([like_val, like_val, like_val, like_val])

            sql = (
                f"SELECT id, title, brand, base_price, image_url, rating, "
                f"review_count, sales_count, tags, sub_category "
                f"FROM product WHERE status = 1 AND ({conditions}) "
                f"ORDER BY sales_count DESC LIMIT 15"
            )
            results = mysql_client.fetch_all(sql, tuple(params))
            logger.info(f"[ShoppingAgent] SQL returned {len(results)} results for terms {search_terms}")

            # 品类过滤：当关键词命中精确品类时，只保留同品类商品，防止掺杂无关品类
            expected_categories = set()
            for kw in keywords:
                if kw in PRECISE_MATCH_MAP:
                    expected_categories.update(PRECISE_MATCH_MAP[kw])
            if expected_categories and results:
                category_filtered = [r for r in results if r.get("sub_category") in expected_categories]
                if category_filtered:
                    logger.info(f"[ShoppingAgent] Category filter: {len(results)} → {len(category_filtered)} (keeping {expected_categories})")
                    results = category_filtered

            # 排除过滤后无结果时，重新用 LLM 从对话上下文中提取品类关键词再搜一次
            if not results:
                logger.info(f"[ShoppingAgent] No match for keywords {keywords}, retrying with context")
                fallback_keywords = self._extract_keywords_with_llm(question, conversation_context)
                if fallback_keywords and fallback_keywords != keywords:
                    fb_conditions = " OR ".join([
                        "(title LIKE %s OR brand LIKE %s OR tags LIKE %s OR sub_category LIKE %s)"
                        for _ in fallback_keywords
                    ])
                    fb_params = []
                    for kw in fallback_keywords:
                        like_val = f"%{kw}%"
                        fb_params.extend([like_val, like_val, like_val, like_val])
                    results = mysql_client.fetch_all(
                        f"SELECT id, title, brand, base_price, image_url, rating, "
                        f"review_count, sales_count, tags, sub_category "
                        f"FROM product WHERE status = 1 AND ({fb_conditions}) "
                        f"ORDER BY sales_count DESC LIMIT 15",
                        tuple(fb_params)
                    )
                    # fallback 结果也做品类过滤
                    fb_categories = set()
                    for kw in fallback_keywords:
                        if kw in PRECISE_MATCH_MAP:
                            fb_categories.update(PRECISE_MATCH_MAP[kw])
                    if fb_categories and results:
                        fb_cat_filtered = [r for r in results if r.get("sub_category") in fb_categories]
                        if fb_cat_filtered:
                            logger.info(f"[ShoppingAgent] Fallback category filter: {len(results)} → {len(fb_cat_filtered)}")
                            results = fb_cat_filtered
                    logger.info(f"[ShoppingAgent] Fallback with context returned {len(results)} products")
                if not results:
                    logger.info(f"[ShoppingAgent] Still no match, returning popular products")
                    results = mysql_client.fetch_all(
                        "SELECT id, title, brand, base_price, image_url, rating, "
                        "review_count, sales_count, tags, sub_category "
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
                f"从以下用户问题中提取商品搜索关键词，用于数据库搜索。\n"
                f"只返回关键词，用逗号分隔，不要其他内容。\n"
                f"关键词应该是品牌名、产品类型、功效、成分等有搜索价值的词。\n"
                f"如果用户说的是排除/不要某个品牌，提取原始品类关键词（如上文说手机，用户说不要oppo，则提取\"手机\"）。\n"
                f"{context_hint}\n"
                f"用户问题：{question}\n\n"
                f"关键词："
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
        }

        # 提取英文单词
        en_words = re.findall(r'[a-zA-Z0-9]+', question)
        # 提取中文片段（2字及以上）
        clean = re.sub(r'[，。！？、；：""''【】《》（）\(\)\[\]\{\}<>\?\!\.\,\;\:\"\'\-\—\…\~\`\s]', ' ', question)
        cn_segments = re.findall(r'[一-鿿]{2,}', clean)
        cn_words = [seg for seg in cn_segments if seg not in stop_words]

        # 3字以上片段拆出子串
        for seg in cn_segments:
            if len(seg) >= 3:
                for i in range(len(seg) - 1):
                    sub = seg[i:i+2]
                    if sub not in stop_words and sub not in cn_words:
                        cn_words.append(sub)

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

        # 3. 综合排序：评分权重 0.4 + 销量权重 0.6
        # 先计算各维度最大值用于归一化（MySQL返回Decimal，需转float）
        max_rating = float(max((p.get("rating", 0) or 0) for p in filtered)) or 1
        max_sales = float(max((p.get("sales_count", 0) or 0) for p in filtered)) or 1

        for p in filtered:
            rating_score = float(p.get("rating", 0) or 0) / max_rating
            sales_score = float(p.get("sales_count", 0) or 0) / max_sales
            p["_sort_score"] = rating_score * 0.4 + sales_score * 0.6

        filtered.sort(key=lambda x: x.get("_sort_score", 0), reverse=True)

        # 移除临时排序字段并返回 top N
        for p in filtered:
            p.pop("_sort_score", None)

        return filtered[:limit]

    def _build_product_context(self, products: List[Dict[str, Any]]) -> str:
        """构建商品信息上下文，供 LLM 生成推荐话术"""
        if not products:
            return ""
        lines = ["以下是匹配到的商品信息："]
        for p in products:
            lines.append(
                f"- {p.get('title', '未知')} | 品牌: {p.get('brand', '未知')} | "
                f"价格: ¥{p.get('base_price', 0)} | 评分: {p.get('rating', 0)} | "
                f"销量: {p.get('sales_count', 0)} | 标签: {p.get('tags', '')}"
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
            f"5. 不要编造商品不存在的功能\n"
            f"6. 如果用户有肤质信息，推荐护肤品时说明是否适合该肤质\n"
            f"7. 如果用户有偏好标签，优先推荐与偏好相关的商品\n"
            f"8. 结合当前季节推荐应季商品，如夏季推荐防晒/清爽类，冬季推荐保湿/保暖类\n"
            f"{image_hint}"
        )
        try:
            return self.llm_service.llm.invoke(prompt).content
        except Exception as e:
            logger.error(f"[ShoppingAgent] LLM generation error: {e}")
            # fallback: 简单拼接推荐
            titles = [p.get('title', '') for p in products[:3]]
            return f"为您推荐：{'、'.join(titles)}，这几款都很受欢迎哦！"

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
