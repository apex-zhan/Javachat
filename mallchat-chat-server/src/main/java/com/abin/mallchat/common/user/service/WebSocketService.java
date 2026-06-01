package com.abin.mallchat.common.user.service;

import com.abin.mallchat.common.user.domain.enums.WSBaseResp;
import com.abin.mallchat.common.user.domain.vo.request.ws.WSAuthorize;
import io.netty.channel.Channel;

public interface WebSocketService {
    /**
     * 处理用户登录请求，需要返回一张带code的二维码
     *
     * @param channel
     */
    void handleLoginReq(Channel channel);

    /**
     * 处理所有ws连接的事件
     *
     * @param channel
     */
    void connect(Channel channel);

    /**
     * 处理ws断开连接的事件
     *
     * @param channel
     */
    void removed(Channel channel);

    /**
     * 主动认证登录
     *
     * @param channel
     * @param wsAuthorize
     */
    void authorize(Channel channel, WSAuthorize wsAuthorize);

    /**
     * 扫码用户登录成功通知,清除本地Cache中的loginCode和channel的关系
     */
    Boolean scanLoginSuccess(Integer loginCode, Long uid);

    /**
     * 通知用户扫码成功
     *
     * @param loginCode
     */
    Boolean scanSuccess(Integer loginCode);

    /**
     * 推动消息给所有在线的人
     *
     * @param wsBaseResp 发送的消息体
     * @param skipUid    需要跳过的人
     */
    void sendToAllOnline(WSBaseResp<?> wsBaseResp, Long skipUid);

    /**
     * 推动消息给所有在线的人
     *
     * @param wsBaseResp 发送的消息体
     */
    void sendToAllOnline(WSBaseResp<?> wsBaseResp);

    /**
     * 推动消息给指定的人
     *
     * @param wsBaseResp 发送的消息体
     * @param uid        需要推送的人
     */
    void sendToUid(WSBaseResp<?> wsBaseResp, Long uid);

    /**
     * 处理客户端消息确认(ACK)
     *
     * @param channel netty连接
     * @param ackJson ACK请求JSON
     */
    void handleMsgAck(Channel channel, String ackJson);

    /**
     * 向所有在线连接发送心跳PING
     */
    void sendPingToAll();

    /**
     * 发送心跳PING给指定Channel
     *
     * @param channel netty连接
     */
    void sendPing(Channel channel);

    /**
     * 获取指定用户的所有在线Channel
     *
     * @param uid 用户ID
     * @return Channel列表
     */
    java.util.List<Channel> getUserChannels(Long uid);

    /**
     * 根据设备类型获取用户的Channel
     *
     * @param uid        用户ID
     * @param deviceType 设备类型
     * @return Channel
     */
    Channel getUserChannelByDevice(Long uid, Integer deviceType);

}
