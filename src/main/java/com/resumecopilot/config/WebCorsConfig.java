package com.resumecopilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 开发用 CORS 配置（前后端分离：前端在 frontend/ 目录独立部署，域名/端口与后端不同）。
 *
 * <p>放开范围：/api/** 全部方法 + 任意来源 + 任意请求头，OPTIONS 预检由 Spring
 * MVC 的 CorsProcessor 自动放行（maxAge 缓存 1 小时，减少预检往返）。
 * 特别注意评测接口带 X-Admin-Token 自定义头、advice 接口带 Accept: text/event-stream，
 * 都会被浏览器先发预检——这里放开 allowedHeaders("*") 是这两个接口能跨域工作的前提。</p>
 *
 * <p>【生产收敛】本配置只用于本地开发/演示：allowedOriginPatterns("*") 等于谁都能调，
 * 上生产必须改成白名单真实前端域名（allowedOrigins("https://app.example.com")），
 * 并按需补 exposedHeaders/allowCredentials；更好的做法是把 CORS 交给网关统一做，
 * 删掉本类。写死在这里是"先跑通、后收敛"的明确取舍，不是最终形态。</p>
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 允许所有来源：兼容 file:// 直开（Origin 为 null）与任意本地静态服务器端口
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
