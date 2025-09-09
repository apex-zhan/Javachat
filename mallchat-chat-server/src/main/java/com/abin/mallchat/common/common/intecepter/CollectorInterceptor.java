package com.abin.mallchat.common.common.intecepter;

import cn.hutool.extra.servlet.ServletUtil;
import com.abin.mallchat.common.common.domain.dto.RequestInfo;
import com.abin.mallchat.common.common.utils.RequestHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * 专门的信息收集的拦截器
 */
@Order(1) // 确保该拦截器在TokenInterceptor之前执行
@Slf4j
@Component
public class CollectorInterceptor implements HandlerInterceptor {
    /**
     * 拦截器的前置处理方法，用于收集请求信息
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        RequestInfo info = new RequestInfo();
        info.setUid(Optional.ofNullable(request.getAttribute(TokenInterceptor.ATTRIBUTE_UID)).map(Object::toString).map(Long::parseLong).orElse(null));
        info.setIp(ServletUtil.getClientIP(request));
        //收集请求信息（用户ID、IP地址）并存储到ThreadLocal中，供后续拦截器和业务代码使用
        RequestHolder.set(info);
        return true;
    }

    /**
     * 拦截器的后置处理方法，用于清理请求信息
     *
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        RequestHolder.remove();
    }

}




