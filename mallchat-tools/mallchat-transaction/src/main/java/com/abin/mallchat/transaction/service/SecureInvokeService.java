package com.abin.mallchat.transaction.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.abin.mallchat.transaction.dao.SecureInvokeRecordDao;
import com.abin.mallchat.transaction.domain.dto.SecureInvokeDTO;
import com.abin.mallchat.transaction.domain.entity.SecureInvokeRecord;
import com.abin.mallchat.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Description: 安全执行处理器
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-08-20
 * ----------------------------------------------------------------
 * 关键技术：
 * 1.事务同步器
 * 2.反射调用
 * 3.指数退避
 * 4.递归防护
 */
@Slf4j
@AllArgsConstructor
public class SecureInvokeService {

    public static final double RETRY_INTERVAL_MINUTES = 2D; //重试间隔为2 分钟

    private final SecureInvokeRecordDao secureInvokeRecordDao;

    private final Executor executor;

    /**
     * 定时任务：每五秒全表扫描，批量处理（走索引）
     */
    @Scheduled(cron = "*/5 * * * * ?")
    public void retry() {
        List<SecureInvokeRecord> secureInvokeRecords = secureInvokeRecordDao.getWaitRetryRecords();
        // 查询：下次重试时间 <= 当前时间
//        List<SecureInvokeRecord> records = secureInvokeRecordDao.selectList(
//                new LambdaQueryWrapper<SecureInvokeRecord>()
//                        .eq(SecureInvokeRecord::getStatus, SecureInvokeRecord.STATUS_WAIT)
//                        .le(SecureInvokeRecord::getNextRetryTime, new Date())  // ✅ 走索引
//        );
        for (SecureInvokeRecord secureInvokeRecord : secureInvokeRecords) {
            doAsyncInvoke(secureInvokeRecord);
        }
    }

    /**
     * 按时间片扫描重试。避免全表扫描 TODO
     */
    @Scheduled(fixedDelay = 10000)
    public void retryByTimeSlice() {

    }

    public void save(SecureInvokeRecord record) {
        secureInvokeRecordDao.save(record);
    }

    /**
     * 更新重试记录
     *
     * @param record
     * @param errorMsg
     */
    private void retryRecord(SecureInvokeRecord record, String errorMsg) {
        Integer retryTimes = record.getRetryTimes() + 1;
        SecureInvokeRecord update = new SecureInvokeRecord();
        update.setId(record.getId());
        update.setFailReason(errorMsg);
        update.setNextRetryTime(getNextRetryTime(retryTimes)); //指数退避
        if (retryTimes > record.getMaxRetryTimes()) {
            //超过最大次数，标记为失败
            update.setStatus(SecureInvokeRecord.STATUS_FAIL);
        } else {
            update.setRetryTimes(retryTimes);
        }
        secureInvokeRecordDao.updateById(update);
    }

    /**
     * 指数退避重试策略
     *
     * @param retryTimes
     * @return
     */
    private Date getNextRetryTime(Integer retryTimes) {//或者可以采用退避算法
        double waitMinutes = Math.pow(RETRY_INTERVAL_MINUTES, retryTimes);//重试时间指数上升 2m 4m 8m 16m
        return DateUtil.offsetMinute(new Date(), (int) waitMinutes);
    }

    private void removeRecord(Long id) {
        secureInvokeRecordDao.removeById(id);
    }

    /**
     * 保存记录 + 注册事务同步器
     *
     * @param record
     * @param async
     */
    public void invoke(SecureInvokeRecord record, boolean async) {
        //判断是否在事务中
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        //非事务状态，直接执行，不做任何保证。
        if (!inTransaction) {
            return;
        }
        //保存执行数据
        save(record);
        //注册事务同步器
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @SneakyThrows
            @Override
            public void afterCommit() {
                //事务后执行
                if (async) {
                    //异步执行
                    doAsyncInvoke(record);
                } else {
                    //同步执行
                    doInvoke(record);
                }
            }
        });
    }

    /**
     * 异步执行（使用线程池）
     *
     * @param record
     */
    public void doAsyncInvoke(SecureInvokeRecord record) {
        executor.execute(() -> {
            log.info(Thread.currentThread().getName(), "doAsyncInvoke", record);
            doInvoke(record);
        });
    }

    /**
     * 执行调用方法（核心）
     *
     * @param record
     */
    public void doInvoke(SecureInvokeRecord record) {
        //求快照参数json
        SecureInvokeDTO secureInvokeDTO = record.getSecureInvokeDTO();
        try {
            // 1. 标记这种执行（避免AOP再次拦截）
            SecureInvokeHolder.setInvoking();
            // 2. 反射获取类
            Class<?> beanClass = Class.forName(secureInvokeDTO.getClassName());
            Object bean = SpringUtil.getBean(beanClass);
            // 3. 反射获取方法
            List<String> parameterStrings = JsonUtils.toList(secureInvokeDTO.getParameterTypes(), String.class);
            List<Class<?>> parameterClasses = getParameters(parameterStrings);
            Method method = ReflectUtil.getMethod(beanClass, secureInvokeDTO.getMethodName(), parameterClasses.toArray(new Class[]{}));
            // 4. 反序列化参数
            Object[] args = getArgs(secureInvokeDTO, parameterClasses);
            //5. 执行方法
            method.invoke(bean, args);
            //6. 执行成功删除记录
            removeRecord(record.getId());
        } catch (Throwable e) {
            log.error("SecureInvokeService invoke fail", e);
            //7. 执行失败，等待下次执行
            retryRecord(record, e.getMessage());
        } finally {
            SecureInvokeHolder.invoked();
        }
    }

    @NotNull
    private Object[] getArgs(SecureInvokeDTO secureInvokeDTO, List<Class<?>> parameterClasses) {
        JsonNode jsonNode = JsonUtils.toJsonNode(secureInvokeDTO.getArgs());
        Object[] args = new Object[jsonNode.size()];
        for (int i = 0; i < jsonNode.size(); i++) {
            Class<?> aClass = parameterClasses.get(i);
            args[i] = JsonUtils.nodeToValue(jsonNode.get(i), aClass);
        }
        return args;
    }

    @NotNull
    private List<Class<?>> getParameters(List<String> parameterStrings) {
        return parameterStrings.stream().map(name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                log.error("SecureInvokeService class not fund", e);
            }
            return null;
        }).collect(Collectors.toList());
    }
}
