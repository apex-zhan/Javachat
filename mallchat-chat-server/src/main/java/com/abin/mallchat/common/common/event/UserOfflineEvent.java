package com.abin.mallchat.common.common.event;

import com.abin.mallchat.common.user.domain.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 用户下线事件
 *
 * @author zhongzb create on 2022/08/26
 */
@Getter
public class UserOfflineEvent extends ApplicationEvent {
    private final User user;

    public UserOfflineEvent(Object source, User user) {
        //source是事件源，可以是发布事件的对象，一般传this
        super(source);
        this.user = user;
    }
}
