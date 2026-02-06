package com.abin.mallchat.ai.llm.exception;

/**
 * LLM 异常基类
 * 
 * @author abin
 */
public class LLMException extends RuntimeException {
    
    public LLMException(String message) {
        super(message);
    }
    
    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
