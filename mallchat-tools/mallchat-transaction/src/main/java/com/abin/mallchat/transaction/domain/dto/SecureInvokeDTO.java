package com.abin.mallchat.transaction.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description:
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-08-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureInvokeDTO {
    private String className;       // 类全限定名
    private String methodName;      // 方法名
    private String parameterTypes;  // 参数类型列表JSON
    private String args;            // 参数值JSON
}
