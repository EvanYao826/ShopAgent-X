#!/usr/bin/env python3
"""
Prompt 测试脚本
测试当前 Prompt 的效果
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# 加载环境变量
from dotenv import load_dotenv
load_dotenv()

from core.llm import LLMService

def test_prompt():
    """测试 Prompt 效果"""
    llm_service = LLMService()

    if not llm_service.llm:
        print("LLM 未初始化，请检查 API Key 配置")
        return

    # 测试用例
    test_cases = [
        {
            "question": "推荐一款适合油皮的洗面奶",
            "user_profile": "用户是女性，25岁，油性皮肤",
            "context": ""
        },
        {
            "question": "有什么好用的手机",
            "user_profile": "用户是男性，30岁，预算5000元",
            "context": ""
        },
        {
            "question": "SK-II 神仙水怎么样",
            "user_profile": "用户是女性，28岁",
            "context": "SK-II 神仙水是SK-II的明星产品，含有Pitera成分，能够改善肤质、提亮肤色。价格约1500元/230ml。"
        },
        {
            "question": "Nike 和 Adidas 哪个好",
            "user_profile": "用户是男性，22岁，喜欢运动",
            "context": ""
        }
    ]

    print("=" * 60)
    print("Prompt 效果测试")
    print("=" * 60)

    for i, case in enumerate(test_cases, 1):
        print(f"\n测试 {i}: {case['question']}")
        print(f"用户画像: {case['user_profile']}")
        if case['context']:
            print(f"上下文: {case['context'][:50]}...")
        print("-" * 40)

        try:
            answer = llm_service.get_answer(
                question=case['question'],
                context_docs=[],
                conversation_context=case['context'],
                user_profile=case['user_profile']
            )
            print(f"回复: {answer}")
            print(f"字数: {len(answer)}")
        except Exception as e:
            print(f"错误: {e}")

    print("\n" + "=" * 60)
    print("测试完成")
    print("=" * 60)

if __name__ == "__main__":
    test_prompt()
