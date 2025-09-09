package com.abin.mallchat.common.common.intecepter;

import com.abin.mallchat.common.common.constant.MDCKey;
import com.abin.mallchat.common.common.exception.HttpErrorEnum;
import com.abin.mallchat.common.user.service.LoginService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.Optional;

@Order(-2) //确保该拦截器在CollectorInterceptor之后执行
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String AUTHORIZATION_SCHEMA = "Bearer ";
    public static final String ATTRIBUTE_UID = "uid";

    @Autowired
    private LoginService loginService;


    @Value("${spring.profiles.active:}")
    protected String activeProfile;

    /**
     * 拦截器的前置处理方法，用于验证用户登录状态
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 开发和本地环境直接放行
        if ("dev".equals(activeProfile) || "local".equals(activeProfile)) {
            // 设置一个默认的测试用户ID
            request.setAttribute(ATTRIBUTE_UID, 1L); // 1 是测试用户ID
            MDC.put(MDCKey.UID, "1");
            return true;
        }
        //获取用户登录token
        String token = getToken(request);
        Long validUid = loginService.getValidUid(token);
        if (Objects.nonNull(validUid)) {//有登录态
            request.setAttribute(ATTRIBUTE_UID, validUid);
        } else {
            //没有登录态，判断是否是公开路径
            boolean isPublicURI = isPublicURI(request.getRequestURI());
            if (!isPublicURI) {//又没有登录态，又不是公开路径，直接401
                HttpErrorEnum.ACCESS_DENIED.sendHttpError(response);
                return false;
            }
        }
        //设置MDC上下文，方便日志记录
        MDC.put(MDCKey.UID, String.valueOf(validUid));
        return true;
    }

    /**
     * 拦截器的后置处理方法，用于清理MDC上下文
     *
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        MDC.remove(MDCKey.UID);
    }

    /**
     * 判断是不是公共方法，可以未登录访问的
     *
     * @param requestURI
     */
    private boolean isPublicURI(String requestURI) {
        // 以 /capi/user/public/ 开头的路径被认为是公共路径
        String[] split = requestURI.split("/");
        return split.length > 2 && "public".equals(split[3]);
    }

    /**
     * 获取请求头中的Token
     *
     * @param request
     * @return
     */
    private String getToken(HttpServletRequest request) {
        //从header中去Authorization字段
        String header = request.getHeader(AUTHORIZATION_HEADER);
        return Optional.ofNullable(header)
                //支持Bearer 前缀协议
                .filter(h -> h.startsWith(AUTHORIZATION_SCHEMA))
                //去掉前缀，得到token
                .map(h -> h.substring(AUTHORIZATION_SCHEMA.length()))
                //否则null
                .orElse(null);
    }
}