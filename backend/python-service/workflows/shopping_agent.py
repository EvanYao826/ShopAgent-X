from typing import Dict, Any, Optional, Generator, List
from workflows.base_agent import BaseAgent
from core.vector_store import vector_store
from core.llm import LLMService
from core.mysql_client import mysql_client
from tools.registry import tool_registry
import logging
import json

logger = logging.getLogger(__name__)


class ShoppingAgent(BaseAgent):
    """导购 Agent - 商品推荐、对比、搜索"""

    def __init__(self):
        self.vector_store = vector_store
        self.llm_service = LLMService()

    def recommend(self, question: str, conversation_id: Optional[str] = None,
                  user_id: Optional[str] = None, context: str = "",
                  **kwargs) -> Dict[str, Any]:
        """处理导购请求：检索商品 → 生成推荐话术 + 商品卡片"""
        logger.info(f"[ShoppingAgent] Processing: {question[:50]}...")

        try:
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

            # 3. 从 MySQL 搜索匹配商品
            products = self._search_products(question)
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
            answer = self._generate_recommendation(question, combined_context, products)

            # 7. 构建商品卡片
            product_cards = self._build_product_cards(products)

            # 8. 保存对话记忆
            self._save_to_memory(conversation_id, question, answer)

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
                         **kwargs) -> Generator[str, None, None]:
        """流式导购：先检索商品，再流式生成推荐话术"""
        logger.info(f"[ShoppingAgent] Stream processing: {question[:50]}...")

        try:
            # 检索
            docs = self.vector_store.search(
                query=question, k=8, similarity_threshold=0.6, use_rerank=False
            )
            products = self._search_products(question)
            product_context = self._build_product_context(products)
            sources = self._build_sources(docs)

            # 流式生成
            for chunk in self.llm_service.get_answer_stream(
                question=question,
                context_docs=docs,
                conversation_context=f"{context}\n\n{product_context}" if context else product_context
            ):
                yield chunk

            # 发送商品卡片
            product_cards = self._build_product_cards(products)
            yield json.dumps({
                "type": "product_cards",
                "product_cards": product_cards,
                "sources": sources,
                "task_type": "shopping"
            })

        except Exception as e:
            logger.error(f"[ShoppingAgent] Stream error: {e}", exc_info=True)
            yield json.dumps({
                "type": "error",
                "content": "为您查找商品时遇到问题，请稍后再试。"
            })

    def _search_products(self, question: str) -> List[Dict[str, Any]]:
        """从 MySQL 搜索匹配商品"""
        try:
            # 提取关键词进行商品搜索
            keywords = self._extract_keywords(question)

            # 如果关键词为空，尝试用数据库品牌/品类匹配
            if not keywords:
                keywords = self._match_brands_and_categories(question)

            if not keywords:
                # 无关键词时返回热门商品
                return mysql_client.fetch_all(
                    "SELECT id, title, brand, base_price, image_url, rating, "
                    "review_count, sales_count, tags, sub_category "
                    "FROM product WHERE status = 1 ORDER BY sales_count DESC LIMIT 5"
                )

            # 对中文关键词做子串拆分（"防晒霜" → ["防晒霜", "防晒", "晒霜"]）
            import re
            search_terms = []
            for kw in keywords:
                search_terms.append(kw)
                # 中文关键词拆出2字子串
                cn_chars = re.findall(r'[一-鿿]', kw)
                if len(cn_chars) >= 3:
                    for i in range(len(cn_chars) - 1):
                        sub = ''.join(cn_chars[i:i+2])
                        if sub not in search_terms:
                            search_terms.append(sub)

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
                f"ORDER BY sales_count DESC LIMIT 5"
            )
            return mysql_client.fetch_all(sql, tuple(params))
        except Exception as e:
            logger.error(f"[ShoppingAgent] Product search error: {e}")
            return []

    def _extract_keywords(self, question: str) -> List[str]:
        """从用户问题中提取搜索关键词 - 优先用LLM，fallback到正则"""
        # 优先用 LLM 提取关键词
        llm_keywords = self._extract_keywords_with_llm(question)
        if llm_keywords:
            return llm_keywords

        # Fallback: 正则分词
        return self._extract_keywords_regex(question)

    def _extract_keywords_with_llm(self, question: str) -> List[str]:
        """用 LLM 从用户问题中提取商品搜索关键词"""
        try:
            if not self.llm_service.llm:
                return []

            prompt = (
                f"从以下用户问题中提取商品搜索关键词，用于数据库搜索。\n"
                f"只返回关键词，用逗号分隔，不要其他内容。\n"
                f"关键词应该是品牌名、产品类型、功效、成分等有搜索价值的词。\n\n"
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
                                  products: List[Dict[str, Any]]) -> str:
        """用 LLM 生成推荐话术"""
        if not products:
            return "抱歉，暂时没有找到完全匹配您需求的商品，您可以换个关键词试试。比如：\n- 推荐一款适合油皮的精华\n- 有没有好用的防晒霜\n- 敏感肌可以用什么面膜"

        prompt = (
            f"你是智能导购助手「小智」。请根据用户需求和商品信息，给出专业、贴心的推荐。\n\n"
            f"用户问题：{question}\n\n"
            f"商品信息：\n{context}\n\n"
            f"推荐原则：\n"
            f"1. 先了解用户需求（肤质/预算/使用场景）\n"
            f"2. 推荐时说明理由（成分、功效、性价比）\n"
            f"3. 如有多款可对比说明\n"
            f"4. 主动提醒注意事项\n"
            f"5. 不要编造商品不存在的功能\n"
            f"6. 语气亲切自然，像朋友推荐一样\n"
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
