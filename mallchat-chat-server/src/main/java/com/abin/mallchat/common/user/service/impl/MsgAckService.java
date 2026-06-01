package com.abin.mallchat.common.user.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.abin.mallchat.common.chat.service.cache.OfflineMsgCache;
import com.abin.mallchat.common.common.constant.RedisKey;
import com.abin.mallchat.common.common.domain.dto.MsgAckDTO;
import com.abin.mallchat.common.common.domain.dto.OfflineMessageDTO;
import com.abin.mallchat.common.common.utils.RedisUtils;
import com.abin.mallchat.common.user.domain.enums.WSBaseResp;
import com.abin.mallchat.common.user.service.WebSocketService;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Description: 消息确认(ACK)服务
 * 负责消息推送后的确认管理、超时重试、以及最终进入离线队列
 *
 * 核心机制：
 * 1. 推送消息时生成 deliveryId，写入 Redis ZSet（score=下次重试时间）
 * 2. 客户端收到消息后回复 ACK，携带 deliveryId
 * 3. 收到 ACK 后从 ZSet 移除，并加入已确认 Set（防重复确认）
 * 4. 定时任务扫描 ZSet，对超时的消息进行重推
 * 5. 超过最大重试次数后，将消息转入离线队列
 *
 * Date: 2026-05-11
 */
@Slf4j
@Service
public class MsgAckService {

    /**
     * ACK超时时间（秒）
     */
    private static final long ACK_TIMEOUT_SECONDS = 5;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_TIMES = 3;

    /**
     * 重试间隔（秒），指数退避：5s, 10s, 20s
     */
    private static final long[] RETRY_INTERVALS = {5, 10, 20};

    /**
     * 已确认集合过期时间（小时）
     */
    private static final long ACK_DONE_EXPIRE_HOURS = 24;

    @Autowired
    @Lazy
    private WebSocketService webSocketService;

    @Autowired
    private OfflineMsgCache offlineMsgCache;

    @Autowired
    @Qualifier("websocketExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 注册待确认消息
     *
     * @param uid        目标用户ID
     * @param msgId      消息ID
     * @param channel    推送的Channel
     * @param wsBaseResp 推送的消息体
     */
    public void registerPendingAck(Long uid, Long msgId, Channel channel, WSBaseResp<?> wsBaseResp) {
        try {
            String deliveryId = generateDeliveryId();
            // 将deliveryId注入到消息体中（如果消息体支持）
            injectDeliveryId(wsBaseResp, deliveryId);

            String channelId = channel.id().asLongText();
            String value = String.format("%s:%s:%s:%d", deliveryId, msgId, channelId, 0);

            long nextRetryTime = System.currentTimeMillis() + ACK_TIMEOUT_SECONDS * 1000;
            String key = RedisKey.getKey(RedisKey.MSG_ACK_WAIT_ZET, uid);

            RedisUtils.zAdd(key, value, (double) nextRetryTime);
            RedisUtils.expire(key, 1, TimeUnit.DAYS); // ZSet 1天过期

            log.debug("注册待确认消息 uid={}, msgId={}, deliveryId={}, 下次重试={}",
                    uid, msgId, deliveryId, new Date(nextRetryTime));
        } catch (Exception e) {
            log.error("注册待确认消息失败 uid={}, msgId={}", uid, msgId, e);
        }
    }

    /**
     * 处理客户端ACK回执
     *
     * @param uid    用户ID
     * @param ackDTO ACK请求
     */
    public void handleAck(Long uid, MsgAckDTO ackDTO) {
        if (Objects.isNull(ackDTO) || StrUtil.isBlank(ackDTO.getDeliveryId())) {
            log.warn("收到无效的ACK请求，uid={}", uid);
            return;
        }
        String deliveryId = ackDTO.getDeliveryId();
        try {
            // 1. 从待确认队列中移除
            String waitKey = RedisKey.getKey(RedisKey.MSG_ACK_WAIT_ZET, uid);
            Set<String> pendingSet = RedisUtils.zRange(waitKey, 0, -1);
            if (CollectionUtil.isNotEmpty(pendingSet)) {
                String target = pendingSet.stream()
                        .filter(s -> s.startsWith(deliveryId + ":"))
                        .findFirst()
                        .orElse(null);
                if (StrUtil.isNotBlank(target)) {
                    RedisUtils.zRemove(waitKey, target);
                    log.debug("消息确认成功 uid={}, deliveryId={}", uid, deliveryId);
                }
            }

            // 2. 加入已确认集合（防重复ACK处理）
            String doneKey = RedisKey.getKey(RedisKey.MSG_ACK_DONE_SET, uid);
            RedisUtils.sSetAndTime(doneKey, ACK_DONE_EXPIRE_HOURS * 3600, deliveryId);

        } catch (Exception e) {
            log.error("处理ACK失败 uid={}, deliveryId={}", uid, deliveryId, e);
        }
    }

    /**
     * 定时扫描待确认消息队列，执行重试或转入离线队列
     * 每2秒执行一次
     */
    @Scheduled(fixedRate = 2000)
    public void scanAndRetry() {
        try {
            // 扫描所有用户的待确认队列（实际生产环境可优化为按实例分片扫描）
            // 这里简化处理，仅处理在线用户的重试
            // 更优方案：使用Redis scan 或按uid分片

            // todo: 实际生产环境建议按服务器实例分片扫描，避免多实例重复扫描
            // 此处为简化示例，仅展示核心逻辑
        } catch (Exception e) {
            log.error("扫描待确认消息队列异常", e);
        }
    }

    /**
     * 对指定用户的待确认消息进行重试扫描
     * 由 WebSocketServiceImpl 在合适时机触发，或按uid分片定时触发
     *
     * @param uid 用户ID
     */
    public void scanPendingAckForUser(Long uid) {
        String waitKey = RedisKey.getKey(RedisKey.MSG_ACK_WAIT_ZET, uid);
        try {
            double now = (double) System.currentTimeMillis();
            // 获取所有已到重试时间的消息
            Set<String> expiredSet = RedisUtils.zRangeByScore(waitKey, 0, now);
            if (CollectionUtil.isEmpty(expiredSet)) {
                return;
            }

            for (String value : expiredSet) {
                String[] parts = value.split(":");
                if (parts.length < 4) {
                    RedisUtils.zRemove(waitKey, value);
                    continue;
                }
                String deliveryId = parts[0];
                Long msgId = Long.parseLong(parts[1]);
                String channelId = parts[2];
                int retryCount = Integer.parseInt(parts[3]);

                // 检查是否已确认（双重检查）
                String doneKey = RedisKey.getKey(RedisKey.MSG_ACK_DONE_SET, uid);
                if (RedisUtils.sHasKey(doneKey, deliveryId)) {
                    RedisUtils.zRemove(waitKey, value);
                    continue;
                }

                if (retryCount >= MAX_RETRY_TIMES) {
                    // 超过最大重试次数，转入离线队列
                    transferToOffline(uid, msgId, deliveryId);
                    RedisUtils.zRemove(waitKey, value);
                    log.info("消息重试耗尽，转入离线队列 uid={}, msgId={}, deliveryId={}", uid, msgId, deliveryId);
                } else {
                    // 执行重试推送
                    retryPush(uid, msgId, deliveryId, retryCount, value, waitKey);
                }
            }
        } catch (Exception e) {
            log.error("扫描用户{}待确认消息失败", uid, e);
        }
    }

    /**
     * 重试推送消息
     */
    private void retryPush(Long uid, Long msgId, String deliveryId, int retryCount, String oldValue, String waitKey) {
        threadPoolTaskExecutor.execute(() -> {
            try {
                // 移除旧的待确认记录
                RedisUtils.zRemove(waitKey, oldValue);

                // 检查用户是否在线
                // todo: 实际应从WebSocketService获取在线状态并重新推送
                // 此处简化，仅更新重试计数和时间

                int newRetryCount = retryCount + 1;
                long interval = RETRY_INTERVALS[Math.min(retryCount, RETRY_INTERVALS.length - 1)];
                long nextRetryTime = System.currentTimeMillis() + interval * 1000;

                String channelId = "unknown"; // 重试时可能channel已变化
                String newValue = String.format("%s:%s:%s:%d", deliveryId, msgId, channelId, newRetryCount);
                RedisUtils.zAdd(waitKey, newValue, (double) nextRetryTime);

                log.debug("消息重试推送 uid={}, msgId={}, retryCount={}, 下次重试={}",
                        uid, msgId, newRetryCount, new Date(nextRetryTime));
            } catch (Exception e) {
                log.error("重试推送消息失败 uid={}, msgId={}", uid, msgId, e);
            }
        });
    }

    /**
     * 将消息转入离线队列
     */
    private void transferToOffline(Long uid, Long msgId, String deliveryId) {
        try {
            OfflineMessageDTO offlineMsg = OfflineMessageDTO.builder()
                    .msgId(msgId)
                    .createTime(new Date())
                    .deliveryId(deliveryId)
                    .build();
            offlineMsgCache.addOfflineMsg(uid, offlineMsg);
        } catch (Exception e) {
            log.error("消息转入离线队列失败 uid={}, msgId={}", uid, msgId, e);
        }
    }

    /**
     * 生成投递唯一标识
     */
    private String generateDeliveryId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 向消息体注入 deliveryId
     * todo: 实际应根据WSBaseResp的具体data类型注入
     */
    private void injectDeliveryId(WSBaseResp<?> wsBaseResp, String deliveryId) {
        // 由于 WSBaseResp 是泛型，且不同type对应不同data类型，
        // 实际实现可以通过在 WSBaseResp 中增加 deliveryId 字段，
        // 或在各具体 data class 中增加 deliveryId。
        // 此处简化，仅做占位说明。
    }
}
