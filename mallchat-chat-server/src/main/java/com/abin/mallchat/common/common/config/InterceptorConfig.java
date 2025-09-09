package com.abin.mallchat.common.common.config;

import com.abin.mallchat.common.common.intecepter.BlackInterceptor;
import com.abin.mallchat.common.common.intecepter.CollectorInterceptor;
import com.abin.mallchat.common.common.intecepter.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Description: 配置所有拦截器
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-04-05
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;
    @Autowired
    private CollectorInterceptor collectorInterceptor;
    @Autowired
    private BlackInterceptor blackInterceptor;

    /**
     * 注册拦截器
     *
     * @param registry
     * @Order注解 语义与当前注册顺序并不一致，但不影响实际执行；最终以addInterceptors的顺序为准。
     * 若后续改为通过 MappedInterceptor 或自动扫描注入，才可能受 @Order 影响。建议用一种方式统一顺序管理，避免歧义。
     * Token -> Collector -> Black；为什么是这个顺序？因为Token是基础，Collector是统计，Black是封禁，封禁是最后一步的保障。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/capi/**")
                .order(1);
        registry.addInterceptor(collectorInterceptor)
                .addPathPatterns("/capi/**")
                .order(2);
        registry.addInterceptor(blackInterceptor)
                .addPathPatterns("/capi/**")
                .order(3);
    }
}
