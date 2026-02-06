package com.abin.mallchat.ai.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI模块业务异常
 * 
 * @author zxw
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AIException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    protected Integer errorCode;

    /**
     * 错误信息
     */
    protected String errorMsg;

    public AIException() {
        super();
    }

    public AIException(String errorMsg) {
        super(errorMsg);
        this.errorMsg = errorMsg;
    }

    public AIException(Integer errorCode, String errorMsg) {
        super(errorMsg);
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public AIException(Integer errorCode, String errorMsg, Throwable cause) {
        super(errorMsg, cause);
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public AIException(ErrorEnum error) {
        super(error.getErrorMsg());
        this.errorCode = error.getErrorCode();
        this.errorMsg = error.getErrorMsg();
    }

    public AIException(ErrorEnum error, Throwable cause) {
        super(error.getErrorMsg(), cause);
        this.errorCode = error.getErrorCode();
        this.errorMsg = error.getErrorMsg();
    }

    @Override
    public String getMessage() {
        return errorMsg;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    /**
     * 错误码枚举接口
     */
    public interface ErrorEnum {
        Integer getErrorCode();
        String getErrorMsg();
    }
}
