package com.abin.mallchat.common.chat.dao;

import com.abin.mallchat.common.chat.domain.entity.RoomGroup;
import com.abin.mallchat.common.chat.mapper.RoomGroupMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 群聊房间表 服务实现类
 * </p>
 *
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @since 2023-07-22
 */
@Service
public class RoomGroupDao extends ServiceImpl<RoomGroupMapper, RoomGroup> {

    /**
     * 根据房间ID列表查询房间组信息
     *
     * @param roomIds 房间ID列表
     * @return 房间组列表，包含与传入房间ID匹配的所有房间组信息
     */
    public List<RoomGroup> listByRoomIds(List<Long> roomIds) {
        return lambdaQuery()
                .in(RoomGroup::getRoomId, roomIds)  // 设置查询条件：roomId在roomIds列表中                           // 执行查询并返回结果列表
                .list();
    }

    /**
     * 根据房间ID获取房间组信息
     *
     * @param roomId 房间ID，用于查询对应的房间组
     * @return 返回匹配房间ID的房间组对象，如果未找到则返回null
     */
    public RoomGroup getByRoomId(Long roomId) {
        return lambdaQuery()
                .eq(RoomGroup::getRoomId, roomId)  // 设置查询条件：房间ID相等
                .one();
    }
}
