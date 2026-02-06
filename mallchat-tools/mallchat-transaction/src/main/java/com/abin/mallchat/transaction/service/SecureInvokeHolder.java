package com.abin.mallchat.transaction.service;

import java.util.Objects;

/**
 * Description: 递归防护
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-10-02
 */
public class SecureInvokeHolder {
    //使用ThreadLocal来存放 当前线程是否正在执行
    private static final ThreadLocal<Boolean> INVOKE_THREAD_LOCAL = new ThreadLocal<>();

    public static boolean isInvoking() {
        return Objects.nonNull(INVOKE_THREAD_LOCAL.get());
    }

    public static void setInvoking() {
        INVOKE_THREAD_LOCAL.set(Boolean.TRUE);
    }

    public static void invoked() {
        INVOKE_THREAD_LOCAL.remove();
    }
}
