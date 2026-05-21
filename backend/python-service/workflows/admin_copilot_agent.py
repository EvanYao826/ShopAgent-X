from typing import Dict, Any, Optional, Generator
from core.mysql_client import mysql_client
from workflows.ops_agent import ops_agent
import logging
import json

logger = logging.getLogger(__name__)


class AdminCopilotAgent:
    """管理助手Agent - 专门处理管理端运营分析的工作流"""

    # 操作类型到 ops_agent 分析类型的映射
    _OPS_TYPE_MAP = {
        "knowledge_gap": ("knowledge_gap", "知识缺口分析"),
        "user_activity": ("user_activity", "用户活跃度分析"),
        "full_ops_report": ("full_report", "完整运营报告"),
        "hot_questions": ("hot_questions", "热门问题分析"),
        "knowledge_growth": ("knowledge_growth", "知识库增长趋势"),
        "agent_success_rate": ("agent_success_rate", "Agent成功率"),
        "tool_call_failures": ("tool_call_failures", "工具调用失败排行"),
    }

    def __init__(self):
        self.ops_agent = ops_agent
        self.admin_operations = {
            "stats": "统计分析",
            "knowledge_inspection": "知识巡检",
            "knowledge_gap": "知识缺口分析（P4-3）",
            "unanswered_analysis": "未命中分析",
            "user_activity": "用户活跃度分析",
            "full_ops_report": "完整运营报告（P4-3）",
            "hot_questions": "热门问题日报/周报",
            "knowledge_growth": "知识库增长趋势",
            "agent_success_rate": "Agent成功率分析",
            "tool_call_failures": "工具调用失败排行",
        }

    def handle(self, question: str, conversation_id: Optional[str] = None,
               user_id: Optional[str] = None, context: str = "",
               **kwargs) -> Dict[str, Any]:
        """处理管理助手请求"""
        logger.info(f"[AdminCopilotAgent] Processing admin request: {question[:50]}...")

        try:
            operation = self._parse_operation(question)
            return self._execute_operation(operation, question)
        except Exception as e:
            logger.error(f"[AdminCopilotAgent] Error: {e}", exc_info=True)
            return self._error_result(f"抱歉，处理管理请求时出错：{str(e)}")

    def handle_stream(self, question: str, conversation_id: Optional[str] = None,
                     user_id: Optional[str] = None, context: str = "",
                     **kwargs) -> Generator[str, None, None]:
        """流式处理管理助手请求"""
        logger.info(f"[AdminCopilotAgent] Stream admin request: {question[:50]}...")

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
        """解析操作类型"""
        lower_question = question.lower()

        if any(kw in lower_question for kw in ["知识缺口", "缺口", "未命中", "知识缺口分析"]):
            return "knowledge_gap"
        if any(kw in lower_question for kw in ["运营报告", "完整报告", "全报告", "运营分析"]):
            return "full_ops_report"
        if any(kw in lower_question for kw in ["用户", "活跃度", "活跃用户"]):
            return "user_activity"
        if any(kw in lower_question for kw in ["统计", "报表", "数据", "分析", "多少", "数量"]):
            return "stats"
        if any(kw in lower_question for kw in ["知识", "文档", "巡检", "检查", "质量"]):
            return "knowledge_inspection"
        if any(kw in lower_question for kw in ["热门问题", "问题排行", "top问题", "常见问题"]):
            return "hot_questions"
        if any(kw in lower_question for kw in ["知识库增长", "文档增长", "增长趋势", "新增文档"]):
            return "knowledge_growth"
        if any(kw in lower_question for kw in ["成功率", "失败率", "agent成功", "运行成功"]):
            return "agent_success_rate"
        if any(kw in lower_question for kw in ["工具调用", "工具失败", "工具错误", "工具排行"]):
            return "tool_call_failures"

        return "stats"

    def _execute_operation(self, operation: str, question: str) -> Dict[str, Any]:
        """执行管理操作"""
        try:
            if operation == "stats":
                return self._get_stats()
            elif operation == "knowledge_inspection":
                return self._knowledge_inspection()

            # 以下操作统一委托给 ops_agent
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
        """统一委托操作给 ops_agent，消除重复的包装逻辑"""
        if operation not in self._OPS_TYPE_MAP:
            return self._error_result("抱歉，我暂时无法处理这类管理请求。")

        analysis_type, label = self._OPS_TYPE_MAP[operation]
        logger.info(f"[AdminCopilotAgent] {label} via Ops Agent")
        result = self.ops_agent.analyze(analysis_type, **kwargs)

        if result.get("success"):
            return {
                "answer": result.get("answer", ""),
                "sources": [],
                "has_sources": False,
                "task_type": "admin_copilot",
                "data": result.get("data", {})
            }
        else:
            return self._error_result(f"{label}失败：" + str(result.get("error", "")))

    def _error_result(self, message: str) -> Dict[str, Any]:
        """统一错误返回格式"""
        return {
            "answer": message,
            "sources": [],
            "has_sources": False,
            "task_type": "admin_copilot",
            "error": True
        }

    def _get_stats(self) -> Dict[str, Any]:
        """获取统计数据"""
        try:
            doc_count = mysql_client.fetch_one("SELECT COUNT(*) as count FROM knowledge_doc") or {}
            chunk_count = mysql_client.fetch_one("SELECT COUNT(*) as count FROM knowledge_chunk") or {}
            qa_count = mysql_client.fetch_one("SELECT COUNT(*) as count FROM qa_log") or {}
            user_count = mysql_client.fetch_one("SELECT COUNT(*) as count FROM user") or {}
            unanswered_count = mysql_client.fetch_one("SELECT COUNT(*) as count FROM qa_unanswered") or {}

            answer = f"""📊 系统统计信息

━━━━━━━━━━━━━━━━━━━━━━━━━

📚 知识库：
- 文档数量：{doc_count.get('count', 0)}
- 知识片段：{chunk_count.get('count', 0)}

💬 问答系统：
- 总问答次数：{qa_count.get('count', 0)}
- 未命中问题：{unanswered_count.get('count', 0)}

👥 用户管理：
- 注册用户：{user_count.get('count', 0)}

━━━━━━━━━━━━━━━━━━━━━━━━━

💡 提示：
- 说"知识缺口分析"可以查看知识缺口（P4-3功能）
- 说"完整运营报告"可以获取完整分析（P4-3功能）
"""

            return {
                "answer": answer,
                "sources": [],
                "has_sources": False,
                "task_type": "admin_copilot",
                "data": {
                    "doc_count": doc_count.get('count', 0),
                    "chunk_count": chunk_count.get('count', 0),
                    "qa_count": qa_count.get('count', 0),
                    "user_count": user_count.get('count', 0),
                    "unanswered_count": unanswered_count.get('count', 0)
                }
            }
        except Exception as e:
            logger.error(f"[AdminCopilotAgent] Stats error: {e}", exc_info=True)
            return self._error_result("获取统计数据失败，请稍后重试。")

    def _knowledge_inspection(self) -> Dict[str, Any]:
        """知识巡检 - 调用InspectionAgent"""
        from workflows.inspection_agent import InspectionAgent
        inspection_agent = InspectionAgent()
        return inspection_agent.inspect("full")
