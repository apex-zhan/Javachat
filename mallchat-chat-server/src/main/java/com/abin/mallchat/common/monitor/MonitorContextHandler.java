package com.abin.mallchat.common.monitor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorContextHandler {
    public static final ThreadLocal<MonitorContext> START_TIME = new ThreadLocal<>();

    /**
     * 设置监控山下文
     */
    public static void setContext(MonitorContext context) {
        START_TIME.set(context);
    }

    /**
     * 设置当前上下文
     */
    public static MonitorContext getContext() {
        return START_TIME.get();
    }

    /**
     * 清除监控上下文
     * 因为会占有内存，所以用完之后一定要remove
     */
    public static void removeContext() {
        START_TIME.remove();
    }
}
