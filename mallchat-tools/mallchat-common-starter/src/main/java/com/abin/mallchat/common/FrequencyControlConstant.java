package com.abin.mallchat.common;

/**
 * 限流策略常量
 */
public interface FrequencyControlConstant {

    String TOTAL_COUNT_WITH_IN_FIX_TIME = "TotalCountWithInFixTime";

    String SLIDING_WINDOW = "SlidingWindow";

    String TOKEN_BUCKET = "TokenBucket";
}
