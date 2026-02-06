package com.abin.mallchat.transaction.aspect;

import cn.hutool.core.date.DateUtil;
import com.abin.mallchat.transaction.annotation.SecureInvoke;
import com.abin.mallchat.transaction.domain.dto.SecureInvokeDTO;
import com.abin.mallchat.transaction.domain.entity.SecureInvokeRecord;
import com.abin.mallchat.transaction.service.SecureInvokeHolder;
import com.abin.mallchat.transaction.service.SecureInvokeService;
import com.abin.mallchat.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Description: 安全执行切面
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-04-20
 * ----------------------------------------------------------------
 * **关键点：**
 * 1. **事务检测**：`TransactionSynchronizationManager.isActualTransactionActive()`
 * 2. **递归防护**：`SecureInvokeHolder.isInvoking()` 避免重试时再次拦截
 * 3. **方法序列化**：保存类名、方法名、参数类型、参数值
 * 4. **异步标记**：根据注解决定同步/异步执行
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 1)//确保最先执行
@Component
public class SecureInvokeAspect {
    @Autowired
    private SecureInvokeService secureInvokeService;

    @Around("@annotation(secureInvoke)")
    public Object around(ProceedingJoinPoint joinPoint, SecureInvoke secureInvoke) throws Throwable {
        boolean async = secureInvoke.async();
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        //1.!inTransaction：非事务状态，直接执行，不做任何保证。
        //2.SecureInvokeHolder.isInvoking()：正在重试执行中，直接执行（避免递归）
        if (SecureInvokeHolder.isInvoking() || !inTransaction) {
            return joinPoint.proceed();
        }
        //3.序列化化方法调用信息
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        List<String> parameters = Stream.of(method.getParameterTypes()).map(Class::getName).collect(Collectors.toList());
        SecureInvokeDTO dto = SecureInvokeDTO.builder()
                .args(JsonUtils.toStr(joinPoint.getArgs()))
                .className(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .parameterTypes(JsonUtils.toStr(parameters))
                .build();
        //构建记录
        SecureInvokeRecord record = SecureInvokeRecord.builder()
                .secureInvokeDTO(dto)
                .maxRetryTimes(secureInvoke.maxRetryTimes())
                .nextRetryTime(DateUtil.offsetMinute(new Date(), (int) SecureInvokeService.RETRY_INTERVAL_MINUTES))
                .build();
        //保存记录 + 注册事务同步器
        secureInvokeService.invoke(record, async);
        return null; //异步执行，不返回结果
    }
}
