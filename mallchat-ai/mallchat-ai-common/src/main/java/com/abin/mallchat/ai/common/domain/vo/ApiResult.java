package com.abin.mallchat.ai.common.domain.vo;

import lombok.Data;

/**
 * AI模块通用返回体
 * 避免依赖 mallchat-chat-server 中的 ApiResult
 *
 * @author zxw
 */
@Data
public class ApiResult<T> {
    private Boolean success;
    private Integer errCode;
    private String errMsg;
    private T data;

    public static <T> ApiResult<T> success() {
        ApiResult<T> result = new ApiResult<>();
        result.setSuccess(Boolean.TRUE);
        return result;
    }

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setData(data);
        result.setSuccess(Boolean.TRUE);
        return result;
    }

    public static <T> ApiResult<T> fail(Integer code, String msg) {
        ApiResult<T> result = new ApiResult<>();
        result.setSuccess(Boolean.FALSE);
        result.setErrCode(code);
        result.setErrMsg(msg);
        return result;
    }

    public boolean isSuccess() {
        return Boolean.TRUE.equals(this.success);
    }
}
