package com.abin.mallchat.common.chat.service.strategy.msg;

import com.abin.mallchat.common.common.exception.CommonErrorEnum;
import com.abin.mallchat.common.common.utils.AssertUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Description: 消息处理工厂
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-06-04
 */
public class MsgHandlerFactory {
    //Map中的key为消息类型，value为对应的消息处理器
    private static final Map<Integer, AbstractMsgHandler> STRATEGY_MAP = new HashMap<>();

    /**
     * 用于注册和管理不同类型的消息处理器
     *
     * @param code 这里的code指的是消息类型
     * @param strategy
     */
    public static void register(Integer code, AbstractMsgHandler strategy) {
        STRATEGY_MAP.put(code, strategy);
    }

    /**
     * 获取策略
     *
     * @param code
     * @return
     */
    public static AbstractMsgHandler getStrategyNoNull(Integer code) {
        AbstractMsgHandler strategy = STRATEGY_MAP.get(code);
        AssertUtil.isNotEmpty(strategy, CommonErrorEnum.PARAM_VALID);
        return strategy;
    }
}

