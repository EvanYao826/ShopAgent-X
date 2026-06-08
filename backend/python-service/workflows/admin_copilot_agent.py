from typing import Dict, Any, Optional, Generator
from core.mysql_client import mysql_client
from workflows.ops_agent import ops_agent
import logging
import json

logger = logging.getLogger(__name__)


class AdminCopilotAgent:
    """管理助手Agent - 基于真实数据库数据 + LLM 智能分析"""

    _OPS_TYPE_MAP = {
        "knowledge_gap": ("knowledge_gap", "知识缺口分析"),
        "user_activity": ("user_activity", "用户活跃度分析"),
        "full_ops_report": ("full_report", "完整运营报告"),
        "hot_questions": ("hot_questions", "热门问题分析"),
        "knowledge_growth": ("knowledge_growth", "知识库增长趋势"),
        "agent_success_rate": ("agent_success_rate", "Agent成功率"),
        "tool_call_failures": ("tool_call_failures", "工具调用失败排行"),
    }

    # 管理助手内置知识库：常见管理问题的标准回答要点
    _KNOWLEDGE_BASE = """
【ShopAgent-X 管理助手知识库】

系统架构：
- 三端架构：Android App（Kotlin Compose）→ Java Backend（Spring Boot, :8888）→ Python AI（FastAPI, :8000）
- AI 核心链路：RouterAgent（意图分类）→ ShoppingAgent / ChitChatAgent / KnowledgeQAAgent / AdminCopilotAgent
- 数据库：MySQL 8.4（shop_agent_db），Redis 缓存
- 前端管理后台：React + Vite（:3000）

核心管理功能：
1. 仪表盘 - 查看今日对话数、推荐次数、点击率、满意度、意图分布、7日趋势、热门商品TOP5、最近推荐记录、未命中问题
2. 商品管理 - 管理电商商品库（商品信息、价格、库存、图片）
3. 订单管理 - 查看和处理用户订单
4. 对话管理 - 查看所有用户对话记录
5. 用户管理 - 管理注册用户
6. 知识库管理 - 上传/管理知识文档（支持PDF/Markdown/DOCX），文档经Python服务解析为Chunk存入向量库
7. 知识巡检 - 检测未命中问题聚类、重复文档、低质量Chunk、过期知识、无人访问文档
8. Agent执行记录 - 查看每次AI调用的意图、状态、耗时

常见管理操作：
- "统计" → 返回知识库/用户/问答统计
- "知识巡检" → 对知识库做全面质量检测
- "分析未命中问题" → 查看用户问了但知识库没覆盖到的问题
- "热门问题" → 查看高频问答
- "知识缺口" → 分析知识库覆盖不足的领域

知识库工作机制：
- 文档上传后 Python DocumentParser 按语义分块（semantic策略，chunk_size=500）
- 分块向量化后存入 FAISS 向量库
- 用户提问时 RouterAgent 分类意图 → RetrievalAgent 检索相关 Chunk → LLM 生成回答
- 无法匹配到知识库内容的问题会记录到 qa_unanswered 表
"""

    def __init__(self):
        self.ops_agent = ops_agent

    def handle(self, question: str, conversation_id: Optional[str] = None,
               user_id: Optional[str] = None, context: str = "",
               **kwargs) -> Dict[str, Any]:
        logger.info(f"[AdminCopilotAgent] Processing: {question[:50]}...")
        try:
            operation = self._parse_operation(question)
            if operation != "stats":
                return self._execute_operation(operation, question)
            return self._analyze_with_llm(question)
        except Exception as e:
            logger.error(f"[AdminCopilotAgent] Error: {e}", exc_info=True)
            return self._error_result(f"抱歉，处理管理请求时出错：{str(e)}")

    def handle_stream(self, question: str, conversation_id: Optional[str] = None,
                     user_id: Optional[str] = None, context: str = "",
                     **kwargs) -> Generator[str, None, None]:
        logger.info(f"[AdminCopilotAgent] Stream: {question[:50]}...")
        try:
            operation = self._parse_operation(question)
            if operation in ["knowledge_gap", "full_ops_report"]:
                yield from self.ops_agent.analyze_stream(
                    "knowledge_gap" if operation == "knowledge_gap" else "full_report"
                )
                return
            result = self.handle(question, conversation_id, user_id, context, **kwargs)
            answer = result.get("answer", "")
            for char in answer:
                yield json.dumps({"type": "token", "content": char})
            yield json.dumps({"type": "end", "content": result})
        except Exception as e:
            logger.error(f"[AdminCopilotAgent] Stream error: {e}", exc_info=True)
            yield json.dumps({"type": "error", "error": str(e)})

    def _parse_operation(self, question: str) -> str:
        q = question.lower()
        if any(kw in q for kw in ["知识缺口", "缺口分析"]): return "knowledge_gap"
        if any(kw in q for kw in ["运营报告", "完整报告", "全报告"]): return "full_ops_report"
        if any(kw in q for kw in ["用户活跃", "活跃度", "活跃用户"]): return "user_activity"
        if any(kw in q for kw in ["热门问题", "问题排行", "top问题", "常见问题"]): return "hot_questions"
        if any(kw in q for kw in ["知识库增长", "文档增长", "增长趋势", "新增文档"]): return "knowledge_growth"
        if any(kw in q for kw in ["成功率", "失败率", "agent成功", "运行成功"]): return "agent_success_rate"
        if any(kw in q for kw in ["工具调用", "工具失败", "工具错误", "工具排行"]): return "tool_call_failures"
        if any(kw in q for kw in ["巡检", "文档检查", "质量检测", "重复文档", "低质量", "过期知识", "无人访问"]):
            return "knowledge_inspection"
        return "stats"

    def _execute_operation(self, operation: str, question: str) -> Dict[str, Any]:
        try:
            if operation == "knowledge_inspection":
                return self._knowledge_inspection()
            ops_kwargs = {}
            if operation == "hot_questions":
                ops_kwargs["period"] = "week" if "周" in question else "day"
            elif operation in ("knowledge_growth", "agent_success_rate"):
                ops_kwargs["period"] = "week" if "周" in question else "month"
            return self._delegate_to_ops(operation, **ops_kwargs)
        except Exception as e:
            logger.error(f"[AdminCopilotAgent] Operation error: {e}", exc_info=True)
            return self._error_result(f"执行操作时出错：{str(e)}")

    def _delegate_to_ops(self, operation: str, **kwargs) -> Dict[str, Any]:
        if operation not in self._OPS_TYPE_MAP:
            return self._error_result("抱歉，我暂时无法处理这类管理请求。")
        analysis_type, label = self._OPS_TYPE_MAP[operation]
        logger.info(f"[AdminCopilotAgent] {label} via Ops Agent")
        result = self.ops_agent.analyze(analysis_type, **kwargs)
        if result.get("success"):
            return {"answer": result.get("answer", ""), "sources": [], "has_sources": False, "task_type": "admin_copilot", "data": result.get("data", {})}
        return self._error_result(f"{label}失败：" + str(result.get("error", "")))

    def _error_result(self, message: str) -> Dict[str, Any]:
        return {"answer": message, "sources": [], "has_sources": False, "task_type": "admin_copilot", "error": True}

    def _knowledge_inspection(self) -> Dict[str, Any]:
        from workflows.inspection_agent import InspectionAgent
        return InspectionAgent().inspect("full")

    # ═══════════════════════════════════════════════════════════════
    #  核心：LLM 智能分析（丰富数据 + 内置知识库）
    # ═══════════════════════════════════════════════════════════════

    def _analyze_with_llm(self, question: str) -> Dict[str, Any]:
        try:
            data = self._collect_all_data()
            from core.llm import llm_service
            if not llm_service or not llm_service.llm:
                return self._format_stats(data)

            prompt = self._build_analysis_prompt(question, data)

            from langchain_core.prompts import PromptTemplate
            from langchain_core.output_parsers import StrOutputParser
            pt = PromptTemplate.from_template("{prompt}")
            chain = pt | llm_service.llm | StrOutputParser()
            answer = chain.invoke({"prompt": prompt})

            return {"answer": answer, "sources": [], "has_sources": False, "task_type": "admin_copilot", "data": data}
        except Exception as e:
            logger.error(f"[AdminCopilotAgent] LLM error: {e}", exc_info=True)
            return self._format_stats(self._collect_all_data())

    def _build_analysis_prompt(self, question: str, data: dict) -> str:
        return f"""{self._KNOWLEDGE_BASE}

【系统实时数据】
- 知识库文档数：{data['doc_count']}，Chunk数：{data['chunk_count']}
- 问答日志总数：{data['qa_count']}
- 注册用户数：{data['user_count']}，商品分类数：{data['category_count']}
- 未命中问题总数：{data['unanswered_count']}（高频未命中 TOP5）：
{data['top_unanswered']}
- 管理员对话数：{data['admin_conv_count']}
- 最近文档（TOP3）：{data['recent_docs']}
- 会话总数：{data['total_convs']}

【管理员问题】
{question}

请作为 ShopAgent-X 管理助手，结合上方知识库和系统实时数据，给出专业、具体、有数据支撑的分析回答。要求：
1. 优先用真实数据说话，数据不足时诚实说明
2. 给出可操作的建议
3. 用中文回答，语气专业友好，格式清晰"""

    # ═══════════════════════════════════════════════════════════════
    #  数据采集：覆盖知识库、问答、用户、文档详情
    # ═══════════════════════════════════════════════════════════════

    def _collect_all_data(self) -> dict:
        data = {}
        try:
            data['doc_count'] = self._cnt("knowledge_doc")
            data['chunk_count'] = self._cnt("knowledge_chunk")
            data['qa_count'] = self._cnt("qa_log")
            data['unanswered_count'] = self._cnt("qa_unanswered")
            data['user_count'] = self._cnt("user")
            data['category_count'] = self._cnt("category")
            data['admin_conv_count'] = self._cnt("conversation", "scene='admin_chat'")
            data['total_convs'] = self._cnt("conversation")
            data['top_unanswered'] = self._top_unanswered()
            data['recent_docs'] = self._recent_docs()
        except Exception as e:
            logger.warning(f"[AdminCopilotAgent] Data collection partial: {e}")
        return data

    def _cnt(self, table: str, where: str = "") -> int:
        sql = f"SELECT COUNT(*) as cnt FROM {table}"
        if where:
            sql += f" WHERE {where}"
        return (mysql_client.fetch_one(sql) or {}).get('cnt', 0)

    def _top_unanswered(self) -> str:
        try:
            rows = mysql_client.fetch_all(
                "SELECT question, count FROM qa_unanswered ORDER BY count DESC LIMIT 5") or []
            if not rows:
                return "暂无"
            return "；".join([f"「{r['question'][:30]}」({r['count']}次)" for r in rows])
        except:
            return "暂无"

    def _recent_docs(self) -> str:
        try:
            rows = mysql_client.fetch_all(
                "SELECT doc_name, status, create_time FROM knowledge_doc ORDER BY create_time DESC LIMIT 3") or []
            if not rows:
                return "暂无"
            return "；".join([f"{r['doc_name']}({r['status']})" for r in rows])
        except:
            return "暂无"

    def _format_stats(self, data: dict) -> Dict[str, Any]:
        answer = f"""📊 系统统计

📚 知识库：文档 {data.get('doc_count',0)} 个，Chunk {data.get('chunk_count',0)} 个
💬 问答：总计 {data.get('qa_count',0)} 次，未命中 {data.get('unanswered_count',0)} 个
👥 用户：{data.get('user_count',0)} 人，分类 {data.get('category_count',0)} 个
📋 对话：{data.get('total_convs',0)} 个（管理 {data.get('admin_conv_count',0)} 个）

💡 输入「知识巡检」检测知识库质量，「热门问题」看高频问答。"""
        return {"answer": answer, "sources": [], "has_sources": False, "task_type": "admin_copilot", "data": data}
