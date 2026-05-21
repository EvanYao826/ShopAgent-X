from typing import Optional
from tools.registry import tool_registry
import logging

logger = logging.getLogger(__name__)


class BaseAgent:
    """Agent 基类 - 提供共享的通用方法"""

    def _format_history(self, messages: list) -> str:
        """格式化对话历史为上下文字符串"""
        if not messages:
            return ""
        formatted = []
        for msg in messages:
            role = msg.get("role", "unknown")
            content = msg.get("content", "")
            if role == "system":
                formatted.append(content)
            elif role == "user":
                formatted.append(f"用户: {content}")
            elif role == "assistant":
                formatted.append(f"AI: {content}")
        return "\n".join(formatted)

    def _save_to_memory(self, conversation_id: str, question: str, answer: str):
        """保存对话到会话记忆"""
        if not conversation_id or not tool_registry.has_tool("conversation_memory_write"):
            return
        try:
            tool_registry.invoke_tool(
                "conversation_memory_write",
                {"conversation_id": conversation_id, "role": "user", "content": question}
            )
            tool_registry.invoke_tool(
                "conversation_memory_write",
                {"conversation_id": conversation_id, "role": "assistant", "content": answer}
            )
            logger.info(f"[{self.__class__.__name__}] Saved conversation to memory")
        except Exception as e:
            logger.warning(f"[{self.__class__.__name__}] Failed to write conversation memory: {e}")
