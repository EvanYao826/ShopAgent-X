#!/usr/bin/env python3
"""
LLM 模型测试脚本
支持测试豆包和通义千问两种模型
"""

import os
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

def test_llm_connection():
    """测试 LLM 模型连接"""
    from core.config import config
    from core.llm import llm_service

    provider = config.LLM_PROVIDER
    print("=" * 50)
    print(f"LLM 模型连接测试 (当前: {provider})")
    print("=" * 50)

    # 检查配置
    print(f"\n1. 配置检查:")
    print(f"   LLM Provider: {provider}")

    if provider == "doubao":
        print(f"   豆包 API Key: {'已配置' if config.DOUBAO_API_KEY else '❌ 未配置'}")
        print(f"   Base URL: {config.DOUBAO_BASE_URL}")
        print(f"   Model: {config.DOUBAO_MODEL}")
        if not config.DOUBAO_API_KEY:
            print("\n❌ 错误: 请在 .env 文件中配置 DOUBAO_API_KEY")
            return False
    else:
        print(f"   通义 API Key: {'已配置' if config.DASHSCOPE_API_KEY else '❌ 未配置'}")
        if not config.DASHSCOPE_API_KEY:
            print("\n❌ 错误: 请在 .env 文件中配置 DASHSCOPE_API_KEY")
            return False

    # 测试 LLM 初始化
    print(f"\n2. LLM 初始化:")
    if llm_service.llm:
        print(f"   ✅ LLM 初始化成功")
    else:
        print(f"   ❌ LLM 初始化失败")
        return False

    # 测试简单调用
    print(f"\n3. 模型调用测试:")
    try:
        result = llm_service.generate("你好，请简单介绍一下自己")
        print(f"   ✅ 调用成功")
        print(f"   回复: {result[:100]}...")
        return True
    except Exception as e:
        print(f"   ❌ 调用失败: {e}")
        return False

def test_stream_mode():
    """测试流式输出"""
    from core.llm import llm_service

    print("\n" + "=" * 50)
    print("流式输出测试")
    print("=" * 50)

    if not llm_service.llm:
        print("❌ LLM 未初始化")
        return False

    try:
        print("\n流式回复: ", end="")
        for chunk in llm_service.get_answer_stream(
            "推荐一款适合油皮的洗面奶",
            [],  # 空的上下文文档
            "",  # 空的对话历史
            "用户是男性，25岁"  # 用户画像
        ):
            import json
            data = json.loads(chunk)
            if data["type"] == "token":
                print(data["content"], end="", flush=True)
            elif data["type"] == "end":
                print("\n\n✅ 流式输出测试成功")
                return True
    except Exception as e:
        print(f"\n❌ 流式输出测试失败: {e}")
        return False

if __name__ == "__main__":
    from core.config import config
    provider = config.LLM_PROVIDER
    print(f"开始测试 LLM 模型 (当前: {provider})...\n")

    # 测试连接
    if test_llm_connection():
        # 测试流式
        test_stream_mode()

    print("\n" + "=" * 50)
    print("测试完成")
    print("=" * 50)
    print(f"\n切换模型: 修改 .env 文件中的 LLM_PROVIDER=doubao 或 LLM_PROVIDER=dashscope")
