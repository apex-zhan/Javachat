package com.abin.mallchat.common.common.event;

import com.abin.mallchat.common.user.domain.entity.UserBackpack;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Description: 用户收到物品事件
 */
@Getter
public class ItemReceiveEvent extends ApplicationEvent {
    private UserBackpack userBackpack;

    /**
     * source是场景类型，比如说给新注册的用户发注册徽章，或者点赞数达到多少发点赞徽章等等
     *
     * @param source
     * @param userBackpack
     */
    public ItemReceiveEvent(Object source, UserBackpack userBackpack) {
        super(source);
        this.userBackpack = userBackpack;
    }

}
