package com.abin.mallchat.common.websocket;

import cn.hutool.core.net.url.UrlBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.Optional;

/**
 * Description: 处理HTTP请求头，提取token和IP地址，并设置到Channel的属性中
 */
public class HttpHeadersHandler extends ChannelInboundHandlerAdapter {
    /**
     * 处理HTTP请求头，提取token和IP地址，并设置到Channel的属性中
     * 然后将请求的URI设置为路径，最后将请求传递给下一个处理器。
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            // 将 msg 强制转换为 FullHttpRequest 类型
            FullHttpRequest request = (FullHttpRequest) msg;
            // 使用 Hutool 的 UrlBuilder 解析 HTTP 请求的 URI
            UrlBuilder urlBuilder = UrlBuilder.ofHttp(request.uri());
            // 在握手 HTTP 请求阶段，解析 URL 参数中的 token，并通过 NettyUtil.setAttr 存入 Channel 属性，后续认证使用。
            String token = Optional.ofNullable(urlBuilder.getQuery()).map(k -> k.get("token")).map(CharSequence::toString).orElse("");
            NettyUtil.setAttr(ctx.channel(), NettyUtil.TOKEN, token);
            // 获取请求路径
            request.setUri(urlBuilder.getPath().toString());
            // 从请求头中获取 X-Real-IP，如果没有，则从远程地址获取 IP
            HttpHeaders headers = request.headers();
            String ip = headers.get("X-Real-IP");
            if (StringUtils.isEmpty(ip)) {//如果没经过nginx，就直接获取远端地址
                InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
                ip = address.getAddress().getHostAddress();
            }
            // 将 IP 地址存入 Channel 属性，后续可以通过 NettyUtil.getAttr(ctx.channel(), NettyUtil.IP) 获取
            NettyUtil.setAttr(ctx.channel(), NettyUtil.IP, ip);
            // 移除当前处理器，继续传递请求到下一个处理器
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(request);
        } else {
            // 如果不是 FullHttpRequest 类型，直接传递给下一个处理器
            ctx.fireChannelRead(msg);
        }
        /**
         * 初始化一个 LinkedHashSet 用于存储 HTTP 请求头
         * 为什么使用 LinkedHashSet？因为它保持插入顺序，并且不允许重复元素。
         * 这在处理 HTTP 请求头时很有用，可以确保每个头部只被存储一次，并且可以按插入顺序访问。
         * 但在这里还没有被使用，是为了后续扩展或其他处理。
         */
        LinkedHashSet<String> headers = new LinkedHashSet<>();
    }
}