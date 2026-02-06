package com.abin.mallchat.ai.common.exception;

/**
 * LLM服务异常
 * 
 * @author zxw
 */
public class LLMException extends AIException {
    private static final long serialVersionUID = 1L;

    public LLMException(ErrorEnum error) {
        super(error);
    }

    public LLMException(ErrorEnum error, Throwable cause) {
        super(error, cause);
    }

    public LLMException(String errorMsg) {
        super(errorMsg);
    }

    public LLMException(Integer errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

    public LLMException(Integer errorCode, String errorMsg, Throwable cause) {
        super(errorCode, errorMsg, cause);
    }
}
