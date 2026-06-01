package com.abin.mallchat.common.user.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 用户设备类型枚举，用于多端登录管理和互踢策略
 *
 * Date: 2026-05-11
 */
@AllArgsConstructor
@Getter
public enum DeviceTypeEnum {
    PC(1, "电脑端"),
    APP(2, "移动端"),
    WEB(3, "网页端"),
    ;

    private final Integer type;
    private final String desc;

    private static Map<Integer, DeviceTypeEnum> cache;

    static {
        cache = Arrays.stream(DeviceTypeEnum.values())
                .collect(Collectors.toMap(DeviceTypeEnum::getType, Function.identity()));
    }

    public static DeviceTypeEnum of(Integer type) {
        return cache.get(type);
    }
}
