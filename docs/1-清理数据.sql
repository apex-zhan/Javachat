-- =============================================
-- 清理无用数据，保留真实账号 ID=20000 (zxw)
-- =============================================

USE mallchat;

SET FOREIGN_KEY_CHECKS = 0;

-- 删除所有消息
TRUNCATE TABLE `message`;

-- 删除所有消息标记
TRUNCATE TABLE `message_mark`;

-- 删除所有会话记录
TRUNCATE TABLE `contact`;

-- 删除所有群成员
TRUNCATE TABLE `group_member`;

-- 删除所有群组详情
TRUNCATE TABLE `room_group`;

-- 删除所有单聊房间关系
TRUNCATE TABLE `room_friend`;

-- 删除所有房间
TRUNCATE TABLE `room`;

-- 删除所有好友关系
TRUNCATE TABLE `user_friend`;

-- 删除所有用户申请记录
TRUNCATE TABLE `user_apply`;

-- 删除其他用户的背包和表情包
DELETE FROM `user_backpack` WHERE `uid` != 20000;
DELETE FROM `user_emoji` WHERE `uid` != 20000;

-- 删除测试用户（保留系统用户ID=1和真实用户ID=20000）
DELETE FROM `user` WHERE `id` NOT IN (1, 20000);

SET FOREIGN_KEY_CHECKS = 1;

SELECT '✅ 数据清理完成！已保留账号 ID=20000 (zxw)' AS result;

