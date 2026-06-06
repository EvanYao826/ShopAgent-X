import os
import json
import requests
from typing import AsyncGenerator, Generator
from langchain_core.prompts import PromptTemplate
from langchain_core.output_parsers import StrOutputParser

# 使用统一配置管理模块
from core.config import config

class LLMService:
    def __init__(self):
        # 根据配置选择 LLM 提供商
        provider = config.LLM_PROVIDER
        config.logger.info(f"Initializing LLM with provider: {provider}")

        if provider == "doubao":
            # 使用豆包模型（Doubao-Seed-2.0-lite）
            try:
                from langchain_openai import ChatOpenAI
            except ImportError:
                config.logger.error("langchain_openai not installed. Run: pip install langchain-openai")
                self.llm = None
                return

            api_key = config.DOUBAO_API_KEY
            if not api_key:
                config.logger.warning("DOUBAO_API_KEY not found. LLM features will not work properly.")
                self.llm = None
            else:
                self.llm = ChatOpenAI(
                    model=config.DOUBAO_MODEL,
                    api_key=api_key,
                    base_url=config.DOUBAO_BASE_URL,
                    streaming=True
                )
                config.logger.info(f"Using Doubao model: {config.DOUBAO_MODEL}")
        else:
            # 使用阿里云通义千问（默认）
            from langchain_community.llms import Tongyi

            api_key = config.DASHSCOPE_API_KEY
            if not api_key:
                config.logger.warning("DASHSCOPE_API_KEY not found. LLM features will not work properly.")
                self.llm = None
            else:
                self.llm = Tongyi(
                    model_name="qwen-plus",
                    api_key=api_key,
                    streaming=True
                )
                config.logger.info("Using DashScope qwen-plus model")

        # 优化后的 Prompt 模板
        # 支持对话上下文和知识库上下文
        self.prompt = PromptTemplate.from_template(
            """
            你是智能导购助手「小智」，专注于数码产品、美妆护肤、运动户外等商品推荐和商品知识问答。

            我们商城主要商品类别：
            - 数码产品：智能手机（iPhone、华为、小米、OPPO、vivo）、耳机（AirPods、Sony）、平板（iPad）、笔记本（MacBook、联想）
            - 美妆护肤：面膜、精华、防晒霜、化妆水（SK-II、The Ordinary、AHC）
            - 运动户外：运动鞋（Nike、HOKA）、冲锋衣（The North Face）、背包（Osprey）
            - 食品饮料：零食、坚果、牛奶

            当前季节：{season}
            用户画像：
            {user_profile}

            回答规则（严格遵守）：
            1. 回复控制在80字以内，简洁明了，不要长篇大论
            2. 如果有相关商品信息，必须推荐这些商品，绝对不能说「暂无商品」「没有找到」
            3. 直接推荐2-3款，说明核心卖点即可
            4. 如果没有相关信息，简短告知并建议换个关键词
            5. 不要提及"AI服务不可用"、"系统错误"等技术问题
            6. 【重要】推荐商品时，严格按照「相关商品信息」中列出的顺序来推荐，不要自行调换顺序
            7. 【重要】只回答与商城商品相关的问题。如果用户问的问题与商品无关（如政治、历史、编程等），请礼貌地引导用户咨询商品相关问题
            8. 【重要】不要编造价格、评分、销量等数据，所有数据必须来自商品信息
            9. 结合当前季节推荐应季商品，如夏季推荐防晒/清爽类，冬季推荐保湿/保暖类

            对话历史：
            {conversation_context}

            相关商品信息：
            {knowledge_context}

            用户当前问题：
            {question}

            请给出简短贴心的导购回复（80字以内）：
            """
        )

        # 标题生成模板
        self.summary_prompt = PromptTemplate.from_template(
            """
            请为以下用户问题生成一个简短的标题（Summary）。
            
            用户问题：
            {question}
            
            要求：
            1. 标题应概括问题的主要内容。
            2. 长度控制在10个字以内。
            3. 不需要任何前缀或后缀，直接返回标题文本。
            
            标题：
            """
        )

    """
     * 获取 LLM 的回答
     * @param question 用户问题
     * @param context_docs 上下文文档列表
     * @param conversation_context 对话上下文（可选）
     * @return LLM 的回答
     * """
    def get_answer(self, question: str, context_docs: list, conversation_context: str = "", user_profile: str = "") -> str:
        import time
        start_time = time.time()
        
        if not self.llm:
            # 当没有API密钥时，返回一个友好的默认响应
            config.logger.info(f"LLM get_answer completed in {time.time() - start_time:.4f}s (no API key)")
            return "我是AI知识库助手，很高兴为您服务。由于系统未配置API密钥，我暂时无法提供详细回答。请联系管理员配置DASHSCOPE_API_KEY环境变量以启用完整功能。"

        # 处理包含图片的问题
        image_process_start = time.time()
        processed_question = self.process_question_with_images(question)
        image_process_time = time.time() - image_process_start
        config.logger.info(f"Image processing completed in {image_process_time:.4f}s")

        # 处理知识库上下文
        if not context_docs:
            knowledge_context = "（无相关知识库信息）"
        else:
            knowledge_context = "\n\n".join([
                doc.page_content if hasattr(doc, 'page_content') else str(doc)
                for doc in context_docs
            ])

        # 处理对话上下文 - 过滤掉错误信息
        cleaned_context = self.clean_conversation_context(conversation_context)
        if not cleaned_context or cleaned_context.strip() == "":
            cleaned_context = "（无对话历史）"

        # 构建处理链
        chain = (
            self.prompt
            | self.llm
            | StrOutputParser()
        )

        try:
            llm_start = time.time()
            result = chain.invoke({
                "conversation_context": cleaned_context,
                "knowledge_context": knowledge_context,
                "question": processed_question,
                "user_profile": user_profile or "（未知）"
            })
            llm_time = time.time() - llm_start
            config.logger.info(f"LLM invocation completed in {llm_time:.4f}s")
            config.logger.info(f"LLM get_answer completed in {time.time() - start_time:.4f}s")
            return result
        except Exception as e:
            config.logger.error(f"LLM Error: {e}")
            config.logger.info(f"LLM get_answer completed in {time.time() - start_time:.4f}s (error)")
            return "抱歉，我暂时无法回答这个问题，请稍后再试。"

    def clean_conversation_context(self, context: str) -> str:
        """
        清理对话上下文，移除错误信息，防止污染后续回答
        """
        if not context:
            return ""
        
        # 需要过滤的错误关键词
        error_keywords = [
            "AI服务暂时不可用",
            "服务不可用",
            "系统错误",
            "无法连接",
            "网络错误",
            "超时",
            "API密钥",
            "配置错误"
        ]
        
        # 按行分割
        lines = context.split("\n")
        # 过滤包含错误关键词的行
        cleaned_lines = [
            line for line in lines 
            if not any(keyword in line for keyword in error_keywords)
        ]
        
        return "\n".join(cleaned_lines)

    """
     * 流式获取 LLM 的回答
     * @param question 用户问题
     * @param context_docs 上下文文档列表
     * @param conversation_context 对话上下文（可选）
     * @return 流式生成器，逐个token返回
     * """
    def get_answer_stream(self, question: str, context_docs: list, conversation_context: str = "", user_profile: str = "", season: str = "") -> Generator[str, None, None]:
        import time
        start_time = time.time()
        
        if not self.llm:
            # 当没有API密钥时，返回错误信息
            config.logger.info(f"LLM get_answer_stream completed in {time.time() - start_time:.4f}s (no API key)")
            yield json.dumps({"type": "error", "content": "未配置API密钥"})
            return

        # 处理包含图片的问题
        image_process_start = time.time()
        processed_question = self.process_question_with_images(question)
        image_process_time = time.time() - image_process_start
        config.logger.info(f"Image processing completed in {image_process_time:.4f}s")

        # 处理知识库上下文
        if not context_docs:
            knowledge_context = "（无相关知识库信息）"
        else:
            knowledge_context = "\n\n".join([
                doc.page_content if hasattr(doc, 'page_content') else str(doc)
                for doc in context_docs
            ])

        # 处理对话上下文 - 过滤掉错误信息
        cleaned_context = self.clean_conversation_context(conversation_context)
        if not cleaned_context or cleaned_context.strip() == "":
            cleaned_context = "（无对话历史）"

        # 构建处理链
        chain = (
            self.prompt
            | self.llm
            | StrOutputParser()
        )

        try:
            # 发送开始信号
            yield json.dumps({"type": "start", "content": ""})

            # 流式调用
            llm_start = time.time()
            full_response = ""
            for chunk in chain.stream({
                "conversation_context": cleaned_context,
                "knowledge_context": knowledge_context,
                "question": processed_question,
                "user_profile": user_profile or "（未知）",
                "season": season or "（未知）"
            }):
                full_response += chunk
                yield json.dumps({"type": "token", "content": chunk})
            llm_time = time.time() - llm_start
            config.logger.info(f"LLM stream invocation completed in {llm_time:.4f}s")

            # 发送结束信号
            yield json.dumps({"type": "end", "content": full_response})
            config.logger.info(f"LLM get_answer_stream completed in {time.time() - start_time:.4f}s")

        except Exception as e:
            config.logger.error(f"LLM Stream Error: {e}")
            config.logger.info(f"LLM get_answer_stream completed in {time.time() - start_time:.4f}s (error)")
            yield json.dumps({"type": "error", "content": "暂时无法回答，请稍后再试"})

    def generate_title(self, question: str) -> str:
        import time
        start_time = time.time()
        
        if not self.llm:
            config.logger.info(f"LLM generate_title completed in {time.time() - start_time:.4f}s (no API key)")
            return "New Chat"

        chain = (
            self.summary_prompt
            | self.llm
            | StrOutputParser()
        )
        
        try:
            llm_start = time.time()
            title = chain.invoke({"question": question})
            llm_time = time.time() - llm_start
            # 清理可能的额外空白或引号
            result = title.strip().strip('"').strip("'")
            config.logger.info(f"LLM title generation completed in {llm_time:.4f}s")
            config.logger.info(f"LLM generate_title completed in {time.time() - start_time:.4f}s")
            return result
        except Exception as e:
            config.logger.error(f"LLM Title Generation Error: {e}")
            config.logger.info(f"LLM generate_title completed in {time.time() - start_time:.4f}s (error)")
            return "New Chat"

    def extract_text_from_image(self, image_url: str) -> str:
        """
        识别图片内容：优先用豆包 Vision API（理解图片语义），fallback 到 Tesseract OCR（仅文字）
        """
        try:
            # 处理相对路径，转换为完整URL
            if image_url.startswith('/api/'):
                image_url = f"http://localhost:8888{image_url}"

            config.logger.info(f"Downloading image from: {image_url}")

            # 下载图片
            response = requests.get(image_url, timeout=10)
            response.raise_for_status()
            image_bytes = response.content
            config.logger.info(f"Image downloaded, size: {len(image_bytes)} bytes")

            return self.extract_text_from_image_bytes(image_bytes)

        except Exception as e:
            config.logger.error(f"Error extracting image content: {e}")
            return f"无法识别图片内容: {str(e)}"

    def extract_text_from_image_bytes(self, image_bytes: bytes) -> str:
        """
        识别图片内容（直接接收图片字节）
        优先用豆包 Vision API，fallback 到 Tesseract OCR
        """
        try:
            config.logger.info(f"Processing image bytes, size: {len(image_bytes)} bytes")

            # 优先：豆包 Vision API（全模态模型，支持图像理解）
            if config.DOUBAO_API_KEY:
                return self._recognize_with_doubao_vision(image_bytes)

            # Fallback：Tesseract OCR
            return self._recognize_with_tesseract(image_bytes)

        except Exception as e:
            config.logger.error(f"Error extracting image content from bytes: {e}")
            return f"无法识别图片内容: {str(e)}"

    def _recognize_with_doubao_vision(self, image_bytes: bytes) -> str:
        """用豆包 Vision API 识别图片内容"""
        import base64
        try:
            b64_image = base64.b64encode(image_bytes).decode('utf-8')
            base_url = config.DOUBAO_BASE_URL.rstrip('/')

            resp = requests.post(
                f"{base_url}/chat/completions",
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {config.DOUBAO_API_KEY}"
                },
                json={
                    "model": config.DOUBAO_VISION_MODEL,
                    "messages": [{
                        "role": "user",
                        "content": [
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_image}"}},
                            {"type": "text", "text": "请简短描述这张图片中的商品，包括品类和品牌（如有）。例如：一双白色Nike运动鞋、一部华为手机。只返回描述，不要其他内容。"}
                        ]
                    }],
                    "max_tokens": 100
                },
                timeout=15
            )

            if resp.status_code != 200:
                config.logger.error(f"Doubao Vision API returned {resp.status_code}: {resp.text}")
                raise Exception(f"API返回 {resp.status_code}")

            result = resp.json()
            description = result["choices"][0]["message"]["content"].strip()
            config.logger.info(f"Doubao Vision result: {description}")
            return description
        except Exception as e:
            config.logger.error(f"Doubao Vision API error: {e}, falling back to Tesseract")
            return self._recognize_with_tesseract(image_bytes)

    def _recognize_with_tesseract(self, image_bytes: bytes) -> str:
        """用 Tesseract OCR 提取图片中的文字（fallback）"""
        try:
            import io
            from PIL import Image
            import pytesseract
            if config.TESSERACT_PATH:
                pytesseract.pytesseract.tesseract_cmd = config.TESSERACT_PATH
            image = Image.open(io.BytesIO(image_bytes))
            text = pytesseract.image_to_string(image, lang='chi_sim+eng')
            config.logger.info(f"Tesseract OCR result: {text[:100]}...")
            return text.strip() if text.strip() else "图片中未识别到文字"
        except Exception as e:
            config.logger.error(f"Tesseract OCR error: {e}")
            return "图片识别失败"

    def recognize_voice(self, audio_bytes: bytes, audio_format: str = "m4a") -> str:
        """
        语音识别：调用豆包多模态模型的音频理解能力
        :param audio_bytes: 音频文件字节
        :param audio_format: 音频格式（m4a/wav/mp3 等）
        :return: 识别出的文字
        """
        import base64
        try:
            if not config.DOUBAO_API_KEY:
                config.logger.warning("DOUBAO_API_KEY not configured")
                return "语音识别未配置"

            b64_audio = base64.b64encode(audio_bytes).decode('utf-8')

            # 调用豆包多模态模型，使用音频理解能力
            base_url = config.DOUBAO_BASE_URL.rstrip('/')
            resp = requests.post(
                f"{base_url}/chat/completions",
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {config.DOUBAO_API_KEY}"
                },
                json={
                    "model": config.DOUBAO_VISION_MODEL,
                    "messages": [{
                        "role": "user",
                        "content": [
                            {
                                "type": "input_audio",
                                "input_audio": {
                                    "data": b64_audio,
                                    "format": audio_format
                                }
                            },
                            {
                                "type": "text",
                                "text": "请将这段语音内容转换为文字，只返回识别出的文字，不要添加任何其他内容。"
                            }
                        ]
                    }],
                    "max_tokens": 500
                },
                timeout=60
            )
            config.logger.info(f"Voice recognition request: model={config.DOUBAO_VISION_MODEL}, audio_format={audio_format}, audio_size={len(audio_bytes)}")

            if resp.status_code != 200:
                config.logger.error(f"Doubao API returned {resp.status_code}: {resp.text}")
                return f"语音识别失败: API返回 {resp.status_code}"

            result = resp.json()
            config.logger.info(f"Doubao API response: {json.dumps(result, ensure_ascii=False)[:500]}")

            text = result["choices"][0]["message"]["content"].strip()
            config.logger.info(f"Voice recognition result: {text}")
            return text if text else "未能识别语音内容"

        except Exception as e:
            config.logger.error(f"Voice recognition error: {type(e).__name__}: {e}", exc_info=True)
            return f"语音识别失败: {str(e)}"

    def process_question_with_images(self, question: str) -> str:
        """
        处理包含图片URL的问题，识别图片内容并添加到问题中
        """
        import re
        # 查找图片URL（支持完整URL和相对路径）
        image_urls = re.findall(r'图片URL: (/api/[^\n]+)', question)

        config.logger.info(f"Found image URLs: {image_urls}")

        if image_urls:
            processed_question = question
            for image_url in image_urls:
                # 识别图片内容（豆包 Vision 或 Tesseract OCR）
                image_content = self.extract_text_from_image(image_url)
                processed_question += f"\n\n图片内容: {image_content}"
            return processed_question
        else:
            return question

    def generate(self, prompt: str, temperature: float = 0.7, max_tokens: int = 150) -> str:
        """
        简单的文本生成方法（用于闲聊等场景）
        """
        import time
        start_time = time.time()
        
        if not self.llm:
            config.logger.info(f"LLM generate completed in {time.time() - start_time:.4f}s (no API key)")
            return "我是AI助手，很高兴为您服务。"
        
        try:
            # 使用简单的 prompt
            simple_prompt = PromptTemplate.from_template("{input}")
            chain = simple_prompt | self.llm | StrOutputParser()
            
            result = chain.invoke({"input": prompt})
            
            config.logger.info(f"LLM generate completed in {time.time() - start_time:.4f}s")
            return result
        except Exception as e:
            config.logger.error(f"LLM generate Error: {e}")
            return "抱歉，我暂时无法回答这个问题。"


# 创建单例实例
llm_service = LLMService()

# 导出（保持兼容性）
llm = llm_service