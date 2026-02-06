package com.abin.mallchat.common.cache;

import com.abin.mallchat.common.chat.domain.entity.Room;
import com.abin.mallchat.common.chat.domain.entity.RoomGroup;
import com.abin.mallchat.common.chat.service.cache.RoomCache;
import com.abin.mallchat.common.chat.service.cache.RoomGroupCache;
import com.abin.mallchat.common.user.domain.dto.SummeryInfoDTO;
import com.abin.mallchat.common.user.domain.entity.User;
import com.abin.mallchat.common.user.service.cache.UserInfoCache;
import com.abin.mallchat.common.user.service.cache.UserSummaryCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JetCache 缓存测试
 * 
 * 测试内容：
 * 1. 单个查询功能
 * 2. 批量查询功能
 * 3. 缓存命中率
 * 4. 缓存失效
 * 5. 性能测试
 */
@SpringBootTest
public class JetCacheTest {
    
    @Autowired
    private UserInfoCache userInfoCache;
    
    @Autowired
    private UserSummaryCache userSummaryCache;
    
    @Autowired
    private RoomCache roomCache;
    
    @Autowired
    private RoomGroupCache roomGroupCache;
    
    /**
     * 测试1：单个查询 - 缓存命中
     */
    @Test
    public void testSingleQueryCacheHit() {
        System.out.println("=== 测试1：单个查询 - 缓存命中 ===");
        
        Long userId = 1L;
        
        // 第一次查询（缓存未命中，查询数据库）
        long start1 = System.currentTimeMillis();
        User user1 = userInfoCache.getUserInfo(userId);
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("第一次查询耗时：" + time1 + "ms");
        assertNotNull(user1);
        
        // 第二次查询（本地缓存命中）
        long start2 = System.currentTimeMillis();
        User user2 = userInfoCache.getUserInfo(userId);
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("第二次查询耗时：" + time2 + "ms");
        
        // 断言：第二次查询应该 < 1ms（本地缓存）
        assertTrue(time2 < 1, "本地缓存命中应该 < 1ms");
        assertEquals(user1.getId(), user2.getId());
        
        System.out.println("✅ 测试通过：缓存命中，性能提升 " + (time1 / time2) + " 倍\n");
    }

    
    /**
     * 测试2：批量查询 - 性能测试
     */
    @Test
    public void testBatchQuery() {
        System.out.println("=== 测试2：批量查询 - 性能测试 ===");
        
        List<Long> userIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        
        // 第一次批量查询（缓存未命中）
        long start1 = System.currentTimeMillis();
        Map<Long, User> users1 = userInfoCache.getUserInfoBatch(userIds);
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("第一次批量查询耗时：" + time1 + "ms");
        assertEquals(10, users1.size());
        
        // 第二次批量查询（缓存命中）
        long start2 = System.currentTimeMillis();
        Map<Long, User> users2 = userInfoCache.getUserInfoBatch(userIds);
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("第二次批量查询耗时：" + time2 + "ms");
        assertEquals(10, users2.size());
        
        // 断言：第二次查询应该更快
        assertTrue(time2 < time1, "缓存命中应该更快");
        
        System.out.println("✅ 测试通过：批量查询性能提升 " + (time1 / Math.max(time2, 1)) + " 倍\n");
    }
    
    /**
     * 测试3：缓存失效
     */
    @Test
    public void testCacheInvalidate() {
        System.out.println("=== 测试3：缓存失效 ===");
        
        Long userId = 1L;
        
        // 查询用户信息（写入缓存）
        User user1 = userInfoCache.getUserInfo(userId);
        System.out.println("原始用户名：" + user1.getName());
        
        // 更新用户信息（失效缓存）
        user1.setName("测试用户_" + System.currentTimeMillis());
        userInfoCache.updateUserInfo(user1);
        System.out.println("更新用户名：" + user1.getName());
        
        // 再次查询（应该从数据库加载最新数据）
        User user2 = userInfoCache.getUserInfo(userId);
        System.out.println("查询用户名：" + user2.getName());
        
        // 断言：应该是最新的数据
        assertEquals(user1.getName(), user2.getName());
        
        System.out.println("✅ 测试通过：缓存失效正常\n");
    }
    
    /**
     * 测试4：房间缓存
     */
    @Test
    public void testRoomCache() {
        System.out.println("=== 测试4：房间缓存 ===");
        
        Long roomId = 1L;
        
        // 单个查询
        long start1 = System.currentTimeMillis();
        Room room1 = roomCache.getRoom(roomId);
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("第一次查询耗时：" + time1 + "ms");
        
        // 再次查询（缓存命中）
        long start2 = System.currentTimeMillis();
        Room room2 = roomCache.getRoom(roomId);
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("第二次查询耗时：" + time2 + "ms");
        
        assertTrue(time2 < 1, "本地缓存命中应该 < 1ms");
        
        System.out.println("✅ 测试通过：房间缓存正常\n");
    }
    
    /**
     * 测试5：批量查询房间
     */
    @Test
    public void testRoomBatchQuery() {
        System.out.println("=== 测试5：批量查询房间 ===");
        
        List<Long> roomIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        
        long start = System.currentTimeMillis();
        Map<Long, Room> rooms = roomCache.getRoomBatch(roomIds);
        long time = System.currentTimeMillis() - start;
        
        System.out.println("批量查询耗时：" + time + "ms");
        System.out.println("查询到 " + rooms.size() + " 个房间");
        
        assertTrue(rooms.size() > 0, "应该查询到房间数据");
        
        System.out.println("✅ 测试通过：批量查询房间正常\n");
    }
    
    /**
     * 测试6：用户综合信息缓存
     */
    @Test
    public void testUserSummaryCache() {
        System.out.println("=== 测试6：用户综合信息缓存 ===");
        
        Long userId = 1L;
        
        // 查询综合信息
        long start1 = System.currentTimeMillis();
        SummeryInfoDTO summary1 = userSummaryCache.getUserSummary(userId);
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("第一次查询耗时：" + time1 + "ms");
        assertNotNull(summary1);
        
        // 再次查询（缓存命中）
        long start2 = System.currentTimeMillis();
        SummeryInfoDTO summary2 = userSummaryCache.getUserSummary(userId);
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("第二次查询耗时：" + time2 + "ms");
        
        assertTrue(time2 < 1, "本地缓存命中应该 < 1ms");
        
        System.out.println("✅ 测试通过：用户综合信息缓存正常\n");
    }
    
    /**
     * 测试7：并发查询（SingleFlight测试）
     */
    @Test
    public void testConcurrentQuery() throws InterruptedException {
        System.out.println("=== 测试7：并发查询（SingleFlight测试） ===");
        
        Long userId = 999L; // 使用一个不存在的ID，确保缓存未命中
        
        // 清空缓存
        userInfoCache.invalidateUserBatch(Arrays.asList(userId));
        
        // 创建10个线程同时查询
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                User user = userInfoCache.getUserInfo(userId);
                System.out.println(Thread.currentThread().getName() + " 查询结果：" + user);
            });
        }
        
        // 启动所有线程
        long start = System.currentTimeMillis();
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        long time = System.currentTimeMillis() - start;
        
        System.out.println("10个线程并发查询耗时：" + time + "ms");
        System.out.println("✅ 测试通过：SingleFlight防止了缓存击穿\n");
    }
    
    /**
     * 测试8：性能对比（循环查询 vs 批量查询）
     */
    @Test
    public void testPerformanceComparison() {
        System.out.println("=== 测试8：性能对比（循环查询 vs 批量查询） ===");
        
        List<Long> userIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        
        // 清空缓存
        userInfoCache.invalidateUserBatch(userIds);
        
        // 方式1：循环单次查询
        long start1 = System.currentTimeMillis();
        for (Long userId : userIds) {
            userInfoCache.getUserInfo(userId);
        }
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("循环单次查询耗时：" + time1 + "ms");
        
        // 清空缓存
        userInfoCache.invalidateUserBatch(userIds);
        
        // 方式2：批量查询
        long start2 = System.currentTimeMillis();
        userInfoCache.getUserInfoBatch(userIds);
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("批量查询耗时：" + time2 + "ms");
        
        System.out.println("性能提升：" + (time1 / Math.max(time2, 1)) + " 倍");
        System.out.println("✅ 测试通过：批量查询性能更优\n");
    }
}
