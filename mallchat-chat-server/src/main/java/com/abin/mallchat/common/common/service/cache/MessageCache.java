package com.abin.mallchat.common.common.service.cache;

import com.abin.mallchat.common.chat.dao.MessageDao;
import com.abin.mallchat.common.chat.domain.entity.Message;
import com.abin.mallchat.common.common.domain.vo.response.CursorPageBaseResp;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessageCache {
    @Autowired
    private MessageDao messageDao;

    @Cached(
            name = "message:detail:",
            key = "#msgId",
            expire = 1800,
            cacheType = CacheType.BOTH,
            localExpire = 60
    )
    public Message getMessageById(Long msgId) {
        return messageDao.getById(msgId);
    }

    @Cached(
            name = "message:list:",
            key = "'room:' + #roomId + ':cursor:' + #cursor",
            expire = 300,
            cacheType = CacheType.REMOTE
    )
    public CursorPageBaseResp<Message> getRoomMessages(Long roomId, String cursor) {
        // 查询逻辑
        return null;
    }
}
