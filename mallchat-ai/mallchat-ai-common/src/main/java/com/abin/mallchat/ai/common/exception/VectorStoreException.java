package com.abin.mallchat.ai.common.exception;

/**
 * 向量存储异常
 * 
 * @author zxw
 */
public class VectorStoreException extends AIException {
    private static final long serialVersionUID = 1L;

    public VectorStoreException(ErrorEnum error) {
        super(error);
    }

    public VectorStoreException(ErrorEnum error, Throwable cause) {
        super(error, cause);
    }

    public VectorStoreException(String errorMsg) {
        super(errorMsg);
    }

    public VectorStoreException(Integer errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

    public VectorStoreException(Integer errorCode, String errorMsg, Throwable cause) {
        super(errorCode, errorMsg, cause);
    }
}
