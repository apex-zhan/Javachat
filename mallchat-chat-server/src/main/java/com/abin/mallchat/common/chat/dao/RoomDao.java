package com.abin.mallchat.common.chat.dao;

import com.abin.mallchat.common.chat.domain.entity.Room;
import com.abin.mallchat.common.chat.mapper.RoomMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * <p>
 * 房间表 服务实现类
 * </p>
 *
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @since 2023-07-16
 */
@Service
public class RoomDao extends ServiceImpl<RoomMapper, Room> implements IService<Room> {

    public void refreshActiveTime(Long roomId, Long msgId, Date msgTime) {
        lambdaUpdate()
                .eq(Room::getId, roomId)
                //todo 这里为了实现有序，防止并发导致最后消息id错乱，但是lastMsgId初始化时候要设置为0，用ActiveTime时间来排序也是可以的
//                .lt(Room::getLastMsgId, msgId)
                .set(Room::getLastMsgId, msgId)
                .set(Room::getActiveTime, msgTime)
                .update();
    }
}
