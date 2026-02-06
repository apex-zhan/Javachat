package com.abin.mallchat.ai.common.exception;

/**
 * 文档处理异常
 * 
 * @author zxw
 */
public class DocumentProcessingException extends AIException {
    private static final long serialVersionUID = 1L;

    public DocumentProcessingException(ErrorEnum error) {
        super(error);
    }

    public DocumentProcessingException(ErrorEnum error, Throwable cause) {
        super(error, cause);
    }

    public DocumentProcessingException(String errorMsg) {
        super(errorMsg);
    }

    public DocumentProcessingException(Integer errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

    public DocumentProcessingException(Integer errorCode, String errorMsg, Throwable cause) {
        super(errorCode, errorMsg, cause);
    }
}
