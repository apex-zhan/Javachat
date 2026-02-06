package com.abin.mallchat.ai.llm.exception;

/**
 * LLM API 调用异常
 * 
 * @author abin
 */
public class LLMApiException extends LLMException {
    
    public LLMApiException(String message) {
        super(message);
    }
    
    public LLMApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
