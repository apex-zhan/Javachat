-- =============================================
-- 生成测试数据（基于真实账号 ID=20000 zxw）
-- 数据量：5个好友、2个群聊、3个单聊、约30条消息
-- =============================================

USE mallchat;

-- =============================================
-- 1. 创建5个虚拟好友
-- =============================================
INSERT INTO `user` (`id`, `name`, `avatar`, `sex`, `open_id`, `active_status`, `last_opt_time`, `ip_info`, `status`, `create_time`, `update_time`) VALUES
(20001, '小明', 'https://thirdwx.qlogo.cn/mmopen/vi_32/DYAIOgq83eo8kKLibPKq1Mzp5oib6Y7gNFPUk3A9ibHG0ricO1YIzQticLniadDqpNhzU2Jic6KhXqH9sMYJIiar7wh8Ng/132', 1, 'friend_001', 1, NOW(), '{"city":"北京"}', 0, NOW(), NOW()),
(20002, '小红', 'https://thirdwx.qlogo.cn/mmopen/vi_32/Q0j4TwGTfTJQHocdQQ5n3mJIXSXvO7SLibOKCXKkRYibibic6QGZqiaib5SPibibXLJRb7F7kxl0dQCDlC5IHMFpTQDwxQ/132', 2, 'friend_002', 1, NOW(), '{"city":"上海"}', 0, NOW(), NOW()),
(20003, '张三', 'https://thirdwx.qlogo.cn/mmopen/vi_32/POgEwh4mIHO4wMn5yF5xz8libKb0v1bSa9iaDN2ibV6nicwHkn5VHy8tMWBXSfibq2QX3HxJpczF4BVhgYT6nqLq0zg/132', 1, 'friend_003', 2, NOW(), '{"city":"广州"}', 0, NOW(), NOW()),
(20004, '李四', 'https://thirdwx.qlogo.cn/mmopen/vi_32/DYAIOgq83eplTlkYpDp6gTAHvZKMlYqKibLOHgMNiaGBjLaicicmz1gNxbfQnSjW1N5DDBQ8ZjqQ1l4XickPH05PrMg/132', 2, 'friend_004', 1, NOW(), '{"city":"深圳"}', 0, NOW(), NOW()),
(20005, '王五', 'https://thirdwx.qlogo.cn/mmopen/vi_32/Q0j4TwGTfTLBfCp9ibibvWyRUxxNTEuM9L3hJ1RIkMbv6ZEXIEJkjZicJwOy7nCichC6YXUx6GmT9P9aE9ic7g8xZ7A/132', 1, 'friend_005', 1, NOW(), '{"city":"杭州"}', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- =============================================
-- 2. 创建好友关系
-- =============================================
INSERT INTO `user_friend` (`uid`, `friend_uid`, `delete_status`, `create_time`, `update_time`) VALUES
-- zxw的好友
(20000, 20001, 0, NOW(), NOW()),
(20001, 20000, 0, NOW(), NOW()),
(20000, 20002, 0, NOW(), NOW()),
(20002, 20000, 0, NOW(), NOW()),
(20000, 20003, 0, NOW(), NOW()),
(20003, 20000, 0, NOW(), NOW()),
-- 其他好友关系
(20001, 20002, 0, NOW(), NOW()),
(20002, 20001, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- =============================================
-- 3. 创建全员群（ID=1）
-- =============================================
INSERT INTO `room` (`id`, `type`, `hot_flag`, `active_time`, `last_msg_id`, `ext_json`, `create_time`, `update_time`) VALUES
(1, 1, 1, NOW(), NULL, '{"groupName":"全员群"}', NOW(), NOW());

INSERT INTO `room_group` (`id`, `room_id`, `name`, `avatar`, `ext_json`, `delete_status`, `create_time`, `update_time`) VALUES
(1, 1, '全员群', 'https://thirdwx.qlogo.cn/mmopen/vi_32/POgEwh4mIHO4wMn5yF5xz8libKb0v1bSa9iaDN2ibV6nicwHkn5VHy8tMWBXSfibq2QX3HxJpczF4BVhgYT6nqLq0zg/132', '{"desc":"欢迎大家！"}', 0, NOW(), NOW());

-- 全员群成员
INSERT INTO `group_member` (`group_id`, `uid`, `role`, `create_time`, `update_time`) VALUES
(1, 1, 3, NOW(), NOW()),      -- 系统用户（群主）
(1, 20000, 3, NOW(), NOW()),  -- zxw（群主）
(1, 20001, 2, NOW(), NOW()),  -- 小明（管理员）
(1, 20002, 1, NOW(), NOW()),  -- 小红
(1, 20003, 1, NOW(), NOW()),  -- 张三
(1, 20004, 1, NOW(), NOW()),  -- 李四
(1, 20005, 1, NOW(), NOW());  -- 王五

-- =============================================
-- 4. 创建小群聊
-- =============================================
INSERT INTO `room` (`id`, `type`, `hot_flag`, `active_time`, `last_msg_id`, `ext_json`, `create_time`, `update_time`) VALUES
(2, 1, 0, NOW(), NULL, '{"groupName":"周末聚会"}', NOW(), NOW());

INSERT INTO `room_group` (`id`, `room_id`, `name`, `avatar`, `ext_json`, `delete_status`, `create_time`, `update_time`) VALUES
(2, 2, '周末聚会', 'https://thirdwx.qlogo.cn/mmopen/vi_32/Q0j4TwGTfTJQHocdQQ5n3mJIXSXvO7SLibOKCXKkRYibibic6QGZqiaib5SPibibXLJRb7F7kxl0dQCDlC5IHMFpTQDwxQ/132', '{"desc":"周末一起玩！"}', 0, NOW(), NOW());

-- 小群成员
INSERT INTO `group_member` (`group_id`, `uid`, `role`, `create_time`, `update_time`) VALUES
(2, 20000, 3, NOW(), NOW()),  -- zxw（群主）
(2, 20001, 1, NOW(), NOW()),  -- 小明
(2, 20002, 1, NOW(), NOW()),  -- 小红
(2, 20004, 1, NOW(), NOW());  -- 李四

-- =============================================
-- 5. 创建单聊房间
-- =============================================
INSERT INTO `room` (`id`, `type`, `hot_flag`, `active_time`, `last_msg_id`, `ext_json`, `create_time`, `update_time`) VALUES
(101, 2, 0, NOW(), NULL, NULL, NOW(), NOW()),
(102, 2, 0, NOW(), NULL, NULL, NOW(), NOW()),
(103, 2, 0, NOW(), NULL, NULL, NOW(), NOW());

INSERT INTO `room_friend` (`room_id`, `uid1`, `uid2`, `room_key`, `status`, `create_time`, `update_time`) VALUES
(101, 20000, 20001, '20000_20001', 0, NOW(), NOW()),  -- zxw和小明
(102, 20000, 20002, '20000_20002', 0, NOW(), NOW()),  -- zxw和小红
(103, 20000, 20003, '20000_20003', 0, NOW(), NOW());  -- zxw和张三

-- =============================================
-- 6. 插入消息（精简版）
-- =============================================
INSERT INTO `message` (`room_id`, `from_uid`, `content`, `reply_msg_id`, `status`, `type`, `extra`, `create_time`, `update_time`) VALUES
-- 全员群消息（8条）
(1, 1, '欢迎大家加入全员群！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 120 MINUTE), DATE_SUB(NOW(), INTERVAL 120 MINUTE)),
(1, 20000, '大家好，我是zxw！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 110 MINUTE), DATE_SUB(NOW(), INTERVAL 110 MINUTE)),
(1, 20001, '你好zxw，我是小明', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 105 MINUTE), DATE_SUB(NOW(), INTERVAL 105 MINUTE)),
(1, 20002, '大家好呀~', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 100 MINUTE), DATE_SUB(NOW(), INTERVAL 100 MINUTE)),
(1, 20003, '今天天气真好！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 90 MINUTE), DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
(1, 20000, '是啊，周末大家有空吗？', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 80 MINUTE), DATE_SUB(NOW(), INTERVAL 80 MINUTE)),
(1, 20001, '我可以！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 70 MINUTE), DATE_SUB(NOW(), INTERVAL 70 MINUTE)),
(1, 20004, '我也想去', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 60 MINUTE), DATE_SUB(NOW(), INTERVAL 60 MINUTE)),

-- 周末聚会群消息（5条）
(2, 20000, '这周末我们去哪里玩？', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 180 MINUTE), DATE_SUB(NOW(), INTERVAL 180 MINUTE)),
(2, 20001, '去爬山怎么样？', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 170 MINUTE), DATE_SUB(NOW(), INTERVAL 170 MINUTE)),
(2, 20002, '我觉得可以去游乐园', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 160 MINUTE), DATE_SUB(NOW(), INTERVAL 160 MINUTE)),
(2, 20004, '游乐园不错！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 150 MINUTE), DATE_SUB(NOW(), INTERVAL 150 MINUTE)),
(2, 20000, '那就定游乐园，周六上午9点见', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 140 MINUTE), DATE_SUB(NOW(), INTERVAL 140 MINUTE)),

-- 单聊消息：zxw和小明（6条）
(101, 20000, '小明，在吗？', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 90 MINUTE), DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
(101, 20001, '在的，什么事？', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 85 MINUTE), DATE_SUB(NOW(), INTERVAL 85 MINUTE)),
(101, 20000, '周末聚会记得来啊', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 80 MINUTE), DATE_SUB(NOW(), INTERVAL 80 MINUTE)),
(101, 20001, '好的，没问题！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 75 MINUTE), DATE_SUB(NOW(), INTERVAL 75 MINUTE)),
(101, 20000, '那到时候见~', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 70 MINUTE), DATE_SUB(NOW(), INTERVAL 70 MINUTE)),
(101, 20001, '好的，到时候见！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 65 MINUTE), DATE_SUB(NOW(), INTERVAL 65 MINUTE)),

-- 单聊消息：zxw和小红（4条）
(102, 20000, '小红，最近怎么样？', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 130 MINUTE), DATE_SUB(NOW(), INTERVAL 130 MINUTE)),
(102, 20002, '挺好的呀，你呢？', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 125 MINUTE), DATE_SUB(NOW(), INTERVAL 125 MINUTE)),
(102, 20000, '也不错，周末一起出去玩', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 120 MINUTE), DATE_SUB(NOW(), INTERVAL 120 MINUTE)),
(102, 20002, '好啊！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 115 MINUTE), DATE_SUB(NOW(), INTERVAL 115 MINUTE)),

-- 单聊消息：zxw和张三（5条）
(103, 20000, '张三，在吗？', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 50 MINUTE), DATE_SUB(NOW(), INTERVAL 50 MINUTE)),
(103, 20003, '在的！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 45 MINUTE), DATE_SUB(NOW(), INTERVAL 45 MINUTE)),
(103, 20000, '帮我看看这个问题', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 40 MINUTE), DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
(103, 20003, '好的，我看看', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 35 MINUTE), DATE_SUB(NOW(), INTERVAL 35 MINUTE)),
(103, 20000, '谢谢！', NULL, 0, 1, '{}', DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 30 MINUTE));

-- =============================================
-- 7. 更新房间最后消息
-- =============================================
UPDATE `room` SET `last_msg_id` = 8, `active_time` = NOW() WHERE `id` = 1;
UPDATE `room` SET `last_msg_id` = 13, `active_time` = NOW() WHERE `id` = 2;
UPDATE `room` SET `last_msg_id` = 19, `active_time` = NOW() WHERE `id` = 101;
UPDATE `room` SET `last_msg_id` = 23, `active_time` = NOW() WHERE `id` = 102;
UPDATE `room` SET `last_msg_id` = 28, `active_time` = NOW() WHERE `id` = 103;

-- =============================================
-- 8. 创建会话记录
-- =============================================
INSERT INTO `contact` (`uid`, `room_id`, `read_time`, `active_time`, `last_msg_id`, `create_time`, `update_time`) VALUES
-- zxw的会话
(20000, 1, NOW(), NOW(), 8, NOW(), NOW()),
(20000, 2, NOW(), NOW(), 13, NOW(), NOW()),
(20000, 101, NOW(), NOW(), 19, NOW(), NOW()),
(20000, 102, NOW(), NOW(), 23, NOW(), NOW()),
(20000, 103, NOW(), NOW(), 28, NOW(), NOW()),
-- 其他用户的会话（确保他们也能看到消息）
(20001, 1, NOW(), NOW(), 8, NOW(), NOW()),
(20001, 2, NOW(), NOW(), 13, NOW(), NOW()),
(20001, 101, NOW(), NOW(), 19, NOW(), NOW()),
(20002, 1, NOW(), NOW(), 8, NOW(), NOW()),
(20002, 2, NOW(), NOW(), 13, NOW(), NOW()),
(20002, 102, NOW(), NOW(), 23, NOW(), NOW())
ON DUPLICATE KEY UPDATE `active_time` = NOW(), `update_time` = NOW();

-- =============================================
-- 完成！
-- =============================================
SELECT '✅ 测试数据生成完成！' AS result;
SELECT '数据统计：' AS info;
SELECT CONCAT('用户数：', COUNT(*)) AS stat FROM `user`;
SELECT CONCAT('群聊：2个（全员群+周末聚会）') AS stat;
SELECT CONCAT('单聊：3个') AS stat;
SELECT CONCAT('好友：3个（小明、小红、张三）') AS stat;
SELECT CONCAT('消息：28条') AS stat;

