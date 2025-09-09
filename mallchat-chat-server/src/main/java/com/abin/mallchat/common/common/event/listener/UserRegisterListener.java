package com.abin.mallchat.common.common.event.listener;

import com.abin.mallchat.common.common.domain.enums.IdempotentEnum;
import com.abin.mallchat.common.common.event.UserRegisterEvent;
import com.abin.mallchat.common.common.utils.LambdaUtils;
import com.abin.mallchat.common.user.dao.UserDao;
import com.abin.mallchat.common.user.domain.entity.User;
import com.abin.mallchat.common.user.domain.enums.ItemEnum;
import com.abin.mallchat.common.user.service.IUserBackpackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 用户上线监听器
 *
 * @author zhongzb create on 2022/08/26
 */
@Slf4j
@Component
public class UserRegisterListener {
    @Autowired
    private UserDao userDao;
    @Autowired
    private IUserBackpackService iUserBackpackService;

    /**
     * 注册成功送一张改名卡
     * 事件可靠吗？
     * 1. 同一个事件被监听多次？
     * 2. 事件丢失？
     * 3. 事件乱序？
     * <p>
     * 为什么我们这里选择了@EventListener而不是@TransactionalEventListener？ 因为我们这里送改名卡的操作不需要和注册操作在同一个事务中，不能影响注册的主流程。
     * 注意：使用@TransactionalEventListener的话需要在方法上加@Transactional注解，否则事务不生效;如果没有加@Transactional需要加fallbackExecution = true属性
     * 为什么加@Async？ 因为送改名卡是个次要操作，不能影响注册的主流程。
     * -------------------------------------------------------
     * //@TransactionalEventListener(classes = UserRegisterEvent.class, phase = TransactionPhase.AFTER_COMMIT,,fallbackExecution = true)
     * //如果说是需要确保注册成功才发放则需要用到事务，但是注册成功了发放失败也没关系，所以这里用异步事务监听器，而且设置了在提交后执行。用这个注解的话就不需要加@Transactional注解了
     *
     * @param event
     */
    @Async
//    @TransactionalEventListener(classes = UserRegisterEvent.class, phase = TransactionPhase.AFTER_COMMIT,fallbackExecution = true)
    @EventListener(classes = UserRegisterEvent.class) //注册卡可以发放失败但是不能影响注册，所以不用事务而是用异步加事件监听
    public void sendCard(UserRegisterEvent event) {
        User user = event.getUser();
        //送一张改名卡
        iUserBackpackService.acquireItem(user.getId(), ItemEnum.MODIFY_NAME_CARD.getId(), IdempotentEnum.UID, user.getId().toString());
    }
/**
 * 这里为什么注释掉了？ 因为这个功能有点鸡肋，意义不大，而且会引起性能问题
 * 1. 送Top10和Top100的徽章意义不大，用户也不在乎这个
 * 2. 每次注册都要查库count用户数量，性能有点问题
 * 3. 如果真的要做这个功能建议改成定时任务，每天统计一次发放一次
 */
//    @Async
//    @EventListener(classes = UserRegisterEvent.class)
//    public void sendBadge(UserRegisterEvent event) {
//        User user = event.getUser();
//        int count = userDao.count();// 性能瓶颈，等注册用户多了直接删掉
//        if (count <= 10) {
//            iUserBackpackService.acquireItem(user.getId(), ItemEnum.REG_TOP10_BADGE.getId(), IdempotentEnum.UID, user.getId().toString());
//        } else if (count <= 100) {
//            iUserBackpackService.acquireItem(user.getId(), ItemEnum.REG_TOP100_BADGE.getId(), IdempotentEnum.UID, user.getId().toString());
//        }
//    }

}
