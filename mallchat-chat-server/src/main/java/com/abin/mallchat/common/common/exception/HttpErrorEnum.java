package com.abin.mallchat.common.common.exception;

import cn.hutool.http.ContentType;
import cn.hutool.json.JSONUtil;
import com.abin.mallchat.common.common.domain.vo.response.ApiResult;
import com.google.common.base.Charsets;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Description: 业务校验异常码
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-03-26
 */
@AllArgsConstructor
@Getter
public enum HttpErrorEnum implements ErrorEnum {
    ACCESS_DENIED(401, "登录失效，请重新登录");
    private Integer httpCode;
    private String msg;

    /**
     * 获取错误码
     *
     * @return
     */
    @Override
    public Integer getErrorCode() {
        return httpCode;
    }

    /**
     * 获取错误信息
     *
     * @return
     */
    @Override
    public String getErrorMsg() {
        return msg;
    }

    /**
     * 发送HTTP错误响应
     *
     * @param response
     * @throws IOException
     */
    public void sendHttpError(HttpServletResponse response) throws IOException {
        // 设置响应状态码和内容类型
        response.setStatus(this.getErrorCode());
        // 创建ApiResult对象并转换为JSON字符串
        ApiResult responseData = ApiResult.fail(this);
        // 设置响应内容类型为JSON，并写入响应体
        response.setContentType(ContentType.JSON.toString(Charsets.UTF_8));
        // 设置响应字符编码
        response.getWriter().write(JSONUtil.toJsonStr(responseData));
    }
}
