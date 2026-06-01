package com.abin.mallchat.ai.llm.domain;

/**
 * LLM 提供商枚举
 * 
 * 支持的LLM提供商列表
 * 
 * @author zxw
 */
public enum LLMProvider {
    
    /**
     * OpenAI (GPT-3.5, GPT-4)
     */
    OPENAI("openai", "OpenAI"),
    
    /**
     * ChatGLM (智谱AI)
     */
    CHATGLM("chatglm", "ChatGLM"),
    
    /**
     * Claude (Anthropic)
     */
    CLAUDE("claude", "Claude"),
    
    /**
     * 通义千问 (阿里云 API)
     */
    QWEN("qwen", "Qwen"),

    /**
     * Qwen2.5-14B (Ollama 本地部署，推荐)
     */
    QWEN_OLLAMA("qwen-ollama", "Qwen2.5-14B (Local)"),

    /**
     * Llama3-70B (Ollama 本地部署，备选)
     */
    LLAMA("llama", "Llama3-70B (Local)"),

    /**
     * 文心一言 (百度)
     */
    ERNIE("ernie", "Ernie"),

    /**
     * Mock模式（本地测试，无需部署LLM）
     */
    MOCK("mock", "Mock (Local Test)");
    
    private final String code;
    private final String name;
    
    LLMProvider(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * 根据代码获取提供商
     */
    public static LLMProvider fromCode(String code) {
        for (LLMProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown LLM provider: " + code);
    }
}
