package com.abin.mallchat.ai.llm.service;

import com.abin.mallchat.ai.llm.domain.LLMProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM 服务工厂
 * 
 * 功能：
 * 1. 管理多个 LLM 提供商
 * 2. 根据配置动态切换提供商
 * 3. 支持运行时切换（可选）
 * 
 * 使用方式：
 * - 通过配置文件指定默认提供商
 * - 通过工厂方法获取指定提供商的服务
 * - 支持降级和故障转移
 * 
 * @author zxw
 */
@Slf4j
@Component
public class LLMServiceFactory {
    
    @Value("${langchain4j.llm.provider:openai}")
    private String defaultProvider;
    
    @Value("${langchain4j.llm.fallback-provider:}")
    private String fallbackProvider;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private Map<LLMProvider, LLMService> serviceMap = new HashMap<>();
    private LLMService defaultService;
    private LLMService fallbackService;
    
    /**
     * 初始化服务映射
     */
    @PostConstruct
    public void init() {
        log.info("Initializing LLM Service Factory");
        log.info("Default provider: {}", defaultProvider);
        log.info("Fallback provider: {}", fallbackProvider);
        
        // 从 Spring 容器中获取所有 LLMService 实现
        Map<String, LLMService> beans = applicationContext.getBeansOfType(LLMService.class);
        
        for (Map.Entry<String, LLMService> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            LLMService service = entry.getValue();
            
            log.info("Found LLM service bean: {}", beanName);
            
            // 根据 bean 名称映射到提供商
            if (beanName.toLowerCase().contains("openai")) {
                serviceMap.put(LLMProvider.OPENAI, service);
            } else if (beanName.toLowerCase().contains("chatglm")) {
                serviceMap.put(LLMProvider.CHATGLM, service);
            } else if (beanName.toLowerCase().contains("claude")) {
                serviceMap.put(LLMProvider.CLAUDE, service);
            } else if (beanName.toLowerCase().contains("qwenllm")) {
                // Qwen2.5-14B via Ollama (推荐)
                serviceMap.put(LLMProvider.QWEN_OLLAMA, service);
            } else if (beanName.toLowerCase().contains("llamallm")) {
                // Llama3-70B via Ollama (备选)
                serviceMap.put(LLMProvider.LLAMA, service);
            } else if (beanName.toLowerCase().contains("qwen")) {
                // 阿里云 Qwen API (兼容旧配置)
                serviceMap.put(LLMProvider.QWEN, service);
            } else if (beanName.toLowerCase().contains("ernie")) {
                serviceMap.put(LLMProvider.ERNIE, service);
            } else if (beanName.toLowerCase().contains("mock")) {
                // Mock模式下的LLM服务
                serviceMap.put(LLMProvider.MOCK, service);
            }
        }
        
        // 设置默认服务
        try {
            LLMProvider provider = LLMProvider.fromCode(defaultProvider);
            defaultService = serviceMap.get(provider);
            
            if (defaultService == null) {
                log.warn("Default provider {} not found, using first available service", defaultProvider);
                defaultService = serviceMap.values().iterator().next();
            }
            
            log.info("Default LLM service set to: {}", defaultProvider);
            
        } catch (Exception e) {
            log.error("Failed to set default LLM service", e);
            // 使用第一个可用的服务
            if (!serviceMap.isEmpty()) {
                defaultService = serviceMap.values().iterator().next();
                log.info("Using first available LLM service as default");
            }
        }
        
        // 设置降级服务
        if (fallbackProvider != null && !fallbackProvider.isEmpty()) {
            try {
                LLMProvider provider = LLMProvider.fromCode(fallbackProvider);
                fallbackService = serviceMap.get(provider);
                
                if (fallbackService != null) {
                    log.info("Fallback LLM service set to: {}", fallbackProvider);
                } else {
                    log.warn("Fallback provider {} not found", fallbackProvider);
                }
                
            } catch (Exception e) {
                log.error("Failed to set fallback LLM service", e);
            }
        }
        
        log.info("LLM Service Factory initialized with {} providers", serviceMap.size());
    }
    
    /**
     * 获取默认的 LLM 服务
     */
    public LLMService getDefaultService() {
        if (defaultService == null) {
            throw new IllegalStateException("No LLM service available");
        }
        return defaultService;
    }
    
    /**
     * 获取指定提供商的 LLM 服务
     */
    public LLMService getService(LLMProvider provider) {
        LLMService service = serviceMap.get(provider);
        if (service == null) {
            log.warn("Provider {} not available, using default service", provider);
            return getDefaultService();
        }
        return service;
    }
    
    /**
     * 获取指定提供商的 LLM 服务（通过代码）
     */
    public LLMService getService(String providerCode) {
        try {
            LLMProvider provider = LLMProvider.fromCode(providerCode);
            return getService(provider);
        } catch (Exception e) {
            log.error("Invalid provider code: {}", providerCode, e);
            return getDefaultService();
        }
    }
    
    /**
     * 获取降级服务
     */
    public LLMService getFallbackService() {
        if (fallbackService != null) {
            return fallbackService;
        }
        
        // 如果没有配置降级服务，返回默认服务
        log.warn("No fallback service configured, using default service");
        return getDefaultService();
    }
    
    /**
     * 检查提供商是否可用
     */
    public boolean isProviderAvailable(LLMProvider provider) {
        return serviceMap.containsKey(provider);
    }
    
    /**
     * 获取所有可用的提供商
     */
    public Map<LLMProvider, LLMService> getAllServices() {
        return new HashMap<>(serviceMap);
    }
    
    /**
     * 动态切换默认提供商（运行时）
     * 
     * 注意：这个方法允许在运行时切换提供商，但不会持久化配置
     */
    public void switchDefaultProvider(LLMProvider provider) {
        LLMService service = serviceMap.get(provider);
        if (service == null) {
            throw new IllegalArgumentException("Provider not available: " + provider);
        }
        
        log.info("Switching default provider from {} to {}", defaultProvider, provider.getCode());
        this.defaultService = service;
        this.defaultProvider = provider.getCode();
    }
    
    /**
     * 获取当前默认提供商
     */
    public String getDefaultProvider() {
        return defaultProvider;
    }
}
