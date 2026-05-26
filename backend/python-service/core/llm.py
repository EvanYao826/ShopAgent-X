import os
import json
import requests
from typing import AsyncGenerator, Generator
from langchain_community.llms import Tongyi
from langchain_core.prompts import PromptTemplate
from langchain_core.output_parsers import StrOutputParser
from PIL import Image
import pytesseract

# 使用统一配置管理模块
from core.config import config

# 配置Tesseract OCR路径（空值时由 parser.py 自动检测）
if config.TESSERACT_PATH:
    pytesseract.pytesseract.tesseract_cmd = config.TESSERACT_PATH

class LLMService:
    def __init__(self):
        # 默认使用阿里云通义千问 (需要设置 DASHSCOPE_API_KEY 环境变量)
        api_key = config.DASHSCOPE_API_KEY
        
        if not api_key:
            config.logger.warning("DASHSCOPE_API_KEY not found. LLM features will not work properly.")
            self.llm = None
        else:
            # 使用 qwen-plus 模型，效果比 turbo 好，适合知识库问答
            # 如果需要更强的推理能力，可以使用 qwen-max
            # 启用流式输出
            self.llm = Tongyi(
                model_name="qwen-plus",
                api_key=api_key,
                streaming=True  # 启用流式输出
            )

        # 优化后的 Prompt 模板
        # 支持对话上下文和知识库上下文
        self.prompt = PromptTemplate.from_template(
            """
            你是智能导购助手「小智」，专注于数码产品、美妆护肤、运动户外等商品推荐。

            我们商城主要商品类别：
            - 数码产品：智能手机（iPhone、华为、小米、OPPO、vivo）、耳机（AirPods、Sony）、平板（iPad）、笔记本（MacBook、联想）
            - 美妆护肤：面膜、精华、防晒霜、化妆水（SK-II、The Ordinary、AHC）
            - 运动户外：运动鞋（Nike、HOKA）、冲锋衣（The North Face）、背包（Osprey）
            - 食品饮料：零食、坚果、牛奶

            用户画像：
            {user_profile}

            回答规则（严格遵守，违反规则将被拒绝）：
            1. 回复必须控制在80字以内，超出部分无效，简洁明了，不要长篇大论
            2. 根据用户画像调整称呼和推荐：如果用户是男性，用"兄弟/哥们"等称呼，推荐男性商品；如果是女性，用"姐妹/小姐姐"等称呼
            3. 不要用与用户性别不符的称呼（如男性用户不要叫"姐妹"）
            4. 如果有相关商品信息，直接推荐2-3款，说明核心卖点即可
            5. 如果没有相关信息，简短告知并建议换个关键词
            6. 不要提及"AI服务不可用"、"系统错误"等技术问题

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
            result = self._truncate_response(result)
            config.logger.info(f"LLM invocation completed in {llm_time:.4f}s")
            config.logger.info(f"LLM get_answer completed in {time.time() - start_time:.4f}s (len={len(result)})")
            return result
        except Exception as e:
            config.logger.error(f"LLM Error: {e}")
            config.logger.info(f"LLM get_answer completed in {time.time() - start_time:.4f}s (error)")
            return "抱歉，我暂时无法回答这个问题，请稍后再试。"

    def _truncate_response(self, text: str, max_chars: int = 80) -> str:
        """
        截断回复到指定字数，保留最后一个完整句子。
        作为 prompt 约束的兜底，防止 LLM 忽略长度限制。
        """
        if len(text) <= max_chars:
            return text

        # 按句子截断，在 max_chars 范围内找最后一个句号/感叹号/问号
        truncated = text[:max_chars]
        for sep in ['。', '！', '？', '～', '!', '?', '~', '…']:
            idx = truncated.rfind(sep)
            if idx > max_chars // 3:  # 至少保留 1/3 内容
                return truncated[:idx + 1]
        # 找不到合适断句点，直接截断加省略号
        return truncated.rstrip() + '～'

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
    def get_answer_stream(self, question: str, context_docs: list, conversation_context: str = "", user_profile: str = "") -> Generator[str, None, None]:
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

            # 流式调用 - token 级别截断，超过 80 字停止输出
            llm_start = time.time()
            full_response = ""
            sent_length = 0
            max_chars = 80
            for chunk in chain.stream({
                "conversation_context": cleaned_context,
                "knowledge_context": knowledge_context,
                "question": processed_question,
                "user_profile": user_profile or "（未知）"
            }):
                full_response += chunk
                if sent_length < max_chars:
                    # 还在限额内，判断这个 chunk 能输出多少
                    if len(full_response) <= max_chars:
                        # 整个 chunk 都在限额内
                        yield json.dumps({"type": "token", "content": chunk})
                        sent_length = len(full_response)
                    else:
                        # 这个 chunk 超过限额，截断到句子边界
                        truncated = self._truncate_response(full_response, max_chars)
                        remaining = truncated[sent_length:]
                        if remaining:
                            yield json.dumps({"type": "token", "content": remaining})
                        sent_length = len(truncated)
                # 超过限额后不再输出 token，但继续消费 stream 以避免连接错误
            llm_time = time.time() - llm_start
            config.logger.info(f"LLM stream invocation completed in {llm_time:.4f}s")

            # 发送结束信号
            final_response = self._truncate_response(full_response, max_chars)
            yield json.dumps({"type": "end", "content": final_response})
            config.logger.info(f"LLM get_answer_stream completed in {time.time() - start_time:.4f}s (len={len(final_response)})")

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
        从图片URL中提取文字
        """
        try:
            # 处理相对路径，转换为完整URL
            if image_url.startswith('/api/'):
                # 使用后端服务地址
                image_url = f"http://localhost:8080{image_url}"
            
            config.logger.info(f"Downloading image from: {image_url}")
            
            # 下载图片
            response = requests.get(image_url, timeout=10)
            response.raise_for_status()
            
            # 保存到临时文件
            temp_path = os.path.join(config.TEMP_DIR, "temp_image.png")
            with open(temp_path, "wb") as f:
                f.write(response.content)
            
            config.logger.info(f"Image saved to temp file, size: {len(response.content)} bytes")
            
            # 使用OCR提取文字
            image = Image.open(temp_path)
            text = pytesseract.image_to_string(image, lang='chi_sim+eng')
            
            config.logger.info(f"OCR result: {text[:100]}...")  # 打印前100个字符
            
            # 清理临时文件
            if os.path.exists(temp_path):
                os.remove(temp_path)
            
            return text.strip() if text.strip() else "图片中未识别到文字"
        except Exception as e:
            config.logger.error(f"Error extracting text from image: {e}")
            return f"无法从图片中提取文字: {str(e)}"

    def process_question_with_images(self, question: str) -> str:
        """
        处理包含图片URL的问题，提取图片中的文字并添加到问题中
        """
        import re
        # 查找图片URL（支持完整URL和相对路径）
        image_urls = re.findall(r'图片URL: (/api/[^\n]+)', question)
        
        config.logger.info(f"Found image URLs: {image_urls}")
        
        if image_urls:
            processed_question = question
            for image_url in image_urls:
                # 提取图片中的文字
                image_text = self.extract_text_from_image(image_url)
                # 将图片文字添加到问题中
                processed_question += f"\n\n图片内容: {image_text}"
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