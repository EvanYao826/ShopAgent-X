package com.demo.aiknowledge.config;

import com.demo.aiknowledge.interceptor.AdminInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AdminInterceptor adminInterceptor;

    @Value("${product.image.base-path:E:/Python_qb/DEmo/ShopAgent-X/data/ecommerce_agent_dataset}")
    private String productImageBasePath;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Ensure UTF-8 for JSON responses
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                ((MappingJackson2HttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册管理员拦截器
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**") // 拦截所有 /api/admin/ 下的请求
                .excludePathPatterns("/api/admin/login"); // 排除登录接口
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 商品图片静态资源映射
        // 访问路径: http://host:8080/product-images/1_美妆护肤/images/p_beauty_001_live.jpg
        registry.addResourceHandler("/product-images/**")
                .addResourceLocations("file:" + productImageBasePath + "/");
    }
}
