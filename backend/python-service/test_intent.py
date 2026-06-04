#!/usr/bin/env python3
"""
意图分类测试脚本
验证意图分类的准确率
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# 加载环境变量
from dotenv import load_dotenv
load_dotenv()

from intent.classifier import IntentClassifier, IntentType

def test_intent_classification():
    """测试意图分类准确率"""
    classifier = IntentClassifier()

    # 测试用例：(输入, 期望的意图类型)
    test_cases = [
        # 购物导购
        ("推荐一款适合油皮的洗面奶", IntentType.SHOPPING),
        ("有什么好用的手机", IntentType.SHOPPING),
        ("帮我选一款运动鞋", IntentType.SHOPPING),
        ("iPhone 15 值得买吗", IntentType.SHOPPING),
        ("SK-II 神仙水多少钱", IntentType.SHOPPING),
        ("推荐一款面霜", IntentType.SHOPPING),
        ("Nike 和 Adidas 哪个好", IntentType.SHOPPING),

        # 闲聊
        ("你好", IntentType.CHITCHAT),
        ("谢谢", IntentType.CHITCHAT),
        ("你是谁", IntentType.CHITCHAT),
        ("讲个笑话", IntentType.CHITCHAT),
        ("今天天气怎么样", IntentType.CHITCHAT),

        # 知识问答
        ("什么是 RAG", IntentType.KNOWLEDGE_QA),
        ("Python 怎么学", IntentType.KNOWLEDGE_QA),
        ("MySQL 和 Redis 的区别", IntentType.KNOWLEDGE_QA),
        ("什么是微服务架构", IntentType.KNOWLEDGE_QA),

        # 身份查询
        ("我叫什么名字", IntentType.IDENTITY_QUERY),
        ("我是谁", IntentType.IDENTITY_QUERY),
    ]

    correct = 0
    total = len(test_cases)
    failed_cases = []

    for input_text, expected_intent in test_cases:
        result = classifier.classify(input_text)
        is_correct = result.intent == expected_intent

        if is_correct:
            correct += 1
        else:
            failed_cases.append({
                "input": input_text,
                "expected": expected_intent.value,
                "actual": result.intent.value,
                "confidence": result.confidence,
                "reasoning": result.reasoning
            })

    # 输出结果
    print("=" * 60)
    print("意图分类测试结果")
    print("=" * 60)
    print(f"\n准确率: {correct}/{total} ({correct/total*100:.1f}%)")

    if failed_cases:
        print(f"\n失败的用例 ({len(failed_cases)} 个):")
        print("-" * 60)
        for case in failed_cases:
            print(f"输入: {case['input']}")
            print(f"期望: {case['expected']}")
            print(f"实际: {case['actual']}")
            print(f"置信度: {case['confidence']:.2f}")
            print(f"原因: {case['reasoning']}")
            print("-" * 60)
    else:
        print("\n[OK] 所有测试用例通过！")

    return correct / total

if __name__ == "__main__":
    accuracy = test_intent_classification()
    sys.exit(0 if accuracy >= 0.8 else 1)
