package com.abin.mallchat.common.common.config;

import cn.hippo4j.core.executor.DynamicThreadPool;
import cn.hippo4j.core.executor.support.ThreadPoolBuilder;
import com.abin.mallchat.common.common.factory.MyThreadFactory;
import com.abin.mallchat.transaction.annotation.SecureInvokeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Description: 线程池配置
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-04-09
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig implements AsyncConfigurer, SecureInvokeConfigurer {
    /**
     * 项目共用线程池
     */
    public static final String MALLCHAT_EXECUTOR = "mallchatExecutor";
    /**
     * websocket通信线程池
     */
    public static final String WS_EXECUTOR = "websocketExecutor";


    public static final String AICHAT_EXECUTOR = "aichatExecutor";

    /**
     * 对@Async注解的支持
     *
     * @return
     */
    @Override
    public Executor getAsyncExecutor() {
        return mallchatExecutor();
    }

    @Override
    public Executor getSecureInvokeExecutor() {
        return mallchatExecutor();
    }

    /**
     * 项目共用线程池
     *
     * @return
     */
    @Bean(MALLCHAT_EXECUTOR)
    @Primary
    public ThreadPoolTaskExecutor mallchatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setWaitForTasksToCompleteOnShutdown(true);//开启spring支持的优雅停机
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mallchat-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());//满了调用线程执行，认为重要任务
        executor.setThreadFactory(new MyThreadFactory(executor));
        executor.initialize();
        return executor;
    }

    /**
     * websocket通信线程池
     *
     * @return
     */
    @Bean(WS_EXECUTOR)
    public ThreadPoolTaskExecutor websocketExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);//支持同时推送1000人
        executor.setThreadNamePrefix("websocket-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());//满了直接丢弃，默认为不重要消息推送
        executor.setThreadFactory(new MyThreadFactory(executor));
        executor.initialize();
        return executor;
    }

    /**
     * AI聊天线程池
     *
     * @return
     */
    @Bean(AICHAT_EXECUTOR)
    public ThreadPoolTaskExecutor chatAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(15);
        executor.setThreadNamePrefix("aichat-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());//满了直接丢弃，默认为不重要消息推送
        executor.setThreadFactory(new MyThreadFactory(executor));
        return executor;
    }

    /**
     * threadPoolId 为服务端创建的线程池 ID;通过 ThreadPoolBuilder 构建动态线程池，只有 threadFactory、threadPoolId 为必填项，其它参数会从 hippo4j-server 服务拉取。
     *
     * @return
     */
    @Bean
    @DynamicThreadPool
    public ThreadPoolExecutor messageConsumeDynamicExecutor() {
        String threadPoolId = "message-consume";
        ThreadPoolExecutor messageConsumeDynamicExecutor = ThreadPoolBuilder.builder()
                .threadFactory(threadPoolId)
                .threadPoolId(threadPoolId)
                .dynamicPool()
                .build();
        return messageConsumeDynamicExecutor;
    }

    @Bean
    @DynamicThreadPool
    public ThreadPoolExecutor messageProduceDynamicExecutor() {
        String threadPoolId = "message-produce";
        ThreadPoolExecutor messageProduceDynamicExecutor = ThreadPoolBuilder.builder()
                .threadFactory(threadPoolId)
                .threadPoolId(threadPoolId)
                .dynamicPool()
                .build();
        return messageProduceDynamicExecutor;
    }


}
