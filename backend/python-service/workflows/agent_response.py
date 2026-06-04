from dataclasses import dataclass, asdict, field
from typing import List, Dict, Any, Optional


@dataclass
class AgentResponse:
    """统一的 Agent 返回格式"""

    answer: str                          # 回答内容
    sources: List[Dict[str, Any]] = field(default_factory=list)  # 引用来源
    has_sources: bool = False            # 是否有来源
    task_type: str = None                # 任务类型（chitchat/shopping/knowledge/reasoning）
    product_cards: List[Dict[str, Any]] = field(default_factory=list)  # 商品卡片（ShoppingAgent 专用）
    reasoning_steps: List[str] = field(default_factory=list)  # 推理步骤（ReasoningAgent 专用）
    inspection_data: Dict[str, Any] = None  # 巡检数据（InspectionAgent 专用）
    error: bool = False                  # 是否出错
    extra: Dict[str, Any] = None         # 扩展字段

    def to_dict(self) -> Dict[str, Any]:
        """序列化为字典，过滤 None 值"""
        return {k: v for k, v in asdict(self).items() if v is not None}

    @classmethod
    def success(cls, answer: str, task_type: str = None, **kwargs) -> 'AgentResponse':
        """创建成功响应"""
        return cls(answer=answer, task_type=task_type, **kwargs)

    @classmethod
    def error(cls, answer: str, task_type: str = None, **kwargs) -> 'AgentResponse':
        """创建错误响应"""
        return cls(answer=answer, task_type=task_type, error=True, **kwargs)
