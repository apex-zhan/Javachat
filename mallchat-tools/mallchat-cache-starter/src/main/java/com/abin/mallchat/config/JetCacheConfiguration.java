//package com.abin.mallchat.config;
//
//import com.abin.mallchat.common.common.factory.JetCacheCreateCacheFactory;
//import com.abin.mallchat.common.common.utils.JetCacheUtils;
//import com.alicp.jetcache.CacheManager;
//import com.alicp.jetcache.autoconfigure.JetCacheAutoConfiguration;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.boot.autoconfigure.AutoConfigureAfter;
//import org.springframework.boot.autoconfigure.cache.CacheProperties;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Import;
//import org.springframework.context.annotation.Primary;
//
//import javax.annotation.PostConstruct;
//
///**
// * @author ningzhaosheng
// * @date 2022/5/13 10:20:09
// * @description 新增JetCache配置，解决JetCache依赖循环问题
// */
//@Configuration(proxyBeanMethods = false)
//@EnableConfigurationProperties(CacheProperties.class)
//@Import({CaffeineConfiguration.class, CacheRedisConfiguration.class})
//@AutoConfigureAfter(JetCacheAutoConfiguration.class) //
//public class JetCacheConfiguration {
//
//    private static final Logger log = LoggerFactory.getLogger(JetCacheConfiguration.class);
//
//    @PostConstruct
//    public void postConstruct() {
//        log.debug("[KuaFuCloud-Base-Cache] |- SDK [Cache JetCache] Auto Configure.");
//    }
//
//    @Bean
//    @ConditionalOnClass(CacheManager.class)
//    public JetCacheCreateCacheFactory jetCacheCreateCacheFactory(CacheManager jcCacheManager) {
//        JetCacheCreateCacheFactory jetCacheCreateCacheFactory = new JetCacheCreateCacheFactory(jcCacheManager);
//        JetCacheUtils.setJetCacheCreateCacheFactory(jetCacheCreateCacheFactory);
//        log.trace("[KuaFuCloud-Base-Cache] |- Bean [Jet Cache Create Cache Factory] Auto Configure.");
//        return jetCacheCreateCacheFactory;
//    }
//
//    @Bean
//    @Primary
//    @ConditionalOnMissingBean
//    public KuaFuCloudCacheManager kuaFuCloudCacheManager(JetCacheCreateCacheFactory jetCacheCreateCacheFactory, CacheProperties cacheProperties) {
//        KuaFuCloudCacheManager kuaFuCloudCacheManager = new KuaFuCloudCacheManager(jetCacheCreateCacheFactory, cacheProperties);
//        kuaFuCloudCacheManager.setAllowNullValues(cacheProperties.getAllowNullValues());
//        log.trace("[KuaFuCloud-Base-Cache] |- Bean [Jet Cache KuaFuCloud Cache Manager] Auto Configure.");
//        return kuaFuCloudCacheManager;
//    }
//}
//
