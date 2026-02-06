package com.abin.mallchat.common;

import cn.hippo4j.core.enable.EnableDynamicThreadPool;
import com.alicp.jetcache.anno.config.EnableCreateCacheAnnotation;
import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * @author zhongzb
 * @date 2021/05/27
 */
@SpringBootApplication(scanBasePackages = {"com.abin.mallchat"} , exclude = {RocketMQAutoConfiguration.class})
@MapperScan({"com.abin.mallchat.common.**.mapper"})
@ServletComponentScan
@EnableMethodCache(basePackages = "com.abin.mallchat")
@EnableCreateCacheAnnotation
@EnableDynamicThreadPool
public class MallchatCustomApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallchatCustomApplication.class,args);
    }

}