package com.demo.aiknowledge.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * URL工具类 - 将相对路径转换为完整URL
 */
@Component
public class UrlUtil {

    @Value("${server.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * 将相对路径转换为完整URL
     * @param relativePath 相对路径（如 /api/chat/view/image/xxx）
     * @return 完整URL（如 http://localhost:8080/api/chat/view/image/xxx）
     */
    public String toAbsoluteUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return relativePath;
        }
        // 如果已经是完整URL，直接返回
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }
        // 确保相对路径以/开头
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        return baseUrl + relativePath;
    }
}
