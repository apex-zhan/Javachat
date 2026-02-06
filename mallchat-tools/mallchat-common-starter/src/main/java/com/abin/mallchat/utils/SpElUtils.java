package com.abin.mallchat.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Optional;
@Slf4j
public class SpElUtils {
    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public static String parseSpEl(Method method, Object[] args, String spEl) {
        // 1. 获取方法参数名
        String[] params = Optional.ofNullable(parameterNameDiscoverer.getParameterNames(method)).orElse(new String[]{});//解析参数名
        EvaluationContext context = new StandardEvaluationContext();//el解析需要的上下文对象
        for (int i = 0; i < params.length; i++) {
            context.setVariable(params[i], args[i]);//所有参数都作为原材料扔进去
        }
        //解析并计算出表达式
        Expression expression = parser.parseExpression(spEl);
        try {
            return expression.getValue(context, String.class);
        } catch (Exception e) {
            // 记录错误表达式和上下文
            log.error("SpEL parse failed. Expression: {}, Context: {}", spEl, context);
            throw new IllegalArgumentException("Invalid SpEL: " + spEl, e);
        }
    }

    public static String getMethodKey(Method method) {
        //通过反射获取方法的全限定名+方法名 作为key，生成方法的唯一标识符，格式：全限定类名#方法名
        return method.getDeclaringClass() + "#" + method.getName();
    }
}
