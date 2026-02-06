package com.abin.mallchat.constant;

/**
 * 缓存常量管理
 * 建议格式：模块_业务_Key
 */
public class CacheConstant {

    /**
     * 用户信息缓存
     */
    public static final String USER_INFO = "user:info";

    /**
     * 所有的道具缓存
     */
    public static final String ITEM_CONFIG_LIST = "item:config:list";
    
    /**
     * 默认过期时间 (单位：秒)
     */
    public static final int DEFAULT_EXPIRE_SECONDS = 3600;
}