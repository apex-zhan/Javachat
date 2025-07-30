package com.abin.mallchat.common.websocket;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.abin.mallchat.common.user.domain.enums.WSReqTypeEnum;
import com.abin.mallchat.common.user.domain.vo.request.ws.WSAuthorize;
import com.abin.mallchat.common.user.domain.vo.request.ws.WSBaseReq;
import com.abin.mallchat.common.user.service.WebSocketService;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Description: Netty WebSocket 服务器处理器
 */
@Slf4j
@Sharable //标记该处理器可以被多个通道安全共享使用。在Netty中，被@Sharable注解的ChannelHandler可以在多个ChannelPipeline中被添加而不需要创建新实例，需要确保实现线程安全。
public class NettyWebSocketServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private WebSocketService webSocketService;

    // 当web客户端连接后，触发该方法
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.webSocketService = getService();
    }

    // 客户端离线
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        userOffLine(ctx);
    }

    /**
     * 取消绑定
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 可能出现业务判断离线后再次触发 channelInactive
        log.warn("触发 channelInactive 掉线![{}]", ctx.channel().id());
        userOffLine(ctx);
    }

    /**
     * 用户下线处理
     *
     * @param ctx
     */
    private void userOffLine(ChannelHandlerContext ctx) {
        this.webSocketService.removed(ctx.channel());
        ctx.channel().close();
    }

    /**
     * 心跳检查
     *
     * @param ctx 指的是 ChannelHandlerContext 上下文对象
     * @param evt 指的是 IdleStateEvent 事件
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 将事件转换为 IdleStateEvent
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            // 读空闲，即客户端在指定时间内没有发送任何数据
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                // 关闭用户的连接
                userOffLine(ctx);
            }
        } else if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) { //WebSocketServerProtocolHandler负责 HTTP 协议升级为 WebSocket 协议
            this.webSocketService.connect(ctx.channel());// 连接成功后，进行授权
            String token = NettyUtil.getAttr(ctx.channel(), NettyUtil.TOKEN);// 从通道中获取token
            if (StrUtil.isNotBlank(token)) {
                this.webSocketService.authorize(ctx.channel(), new WSAuthorize(token));
            }
        }
        //如果不是 IdleStateEvent 或 HandshakeComplete 事件，则调用父类的处理方法
        else {
            log.warn("未知事件类型: {}", evt.getClass().getSimpleName());
            super.userEventTriggered(ctx, evt);
        }
    }

    // 处理异常
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn("异常发生，异常消息 ={}", cause);
        ctx.channel().close();
    }

    private WebSocketService getService() {
        return SpringUtil.getBean(WebSocketService.class);
    }

    // 读取客户端发送的请求报文
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        WSBaseReq wsBaseReq = JSONUtil.toBean(msg.text(), WSBaseReq.class);
        WSReqTypeEnum wsReqTypeEnum = WSReqTypeEnum.of(wsBaseReq.getType());
        switch (wsReqTypeEnum) {
            case LOGIN:
                //参数channel是为了知道是哪个channel想要申请二维码
                this.webSocketService.handleLoginReq(ctx.channel());
                log.info("请求二维码 = " + msg.text());
                break;
            case HEARTBEAT:
                break;
            default:
                log.info("未知类型");
        }
    }
}
