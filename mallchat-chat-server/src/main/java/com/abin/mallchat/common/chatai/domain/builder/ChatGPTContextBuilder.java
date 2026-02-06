package com.abin.mallchat.common.chatai.domain.builder;

import com.abin.mallchat.common.chatai.domain.ChatGPTContext;

/**
 * ChatGPT上下文构建器类
 * 用于初始化和构建ChatGPT对话的上下文环境
 */
public class ChatGPTContextBuilder {

    /**
     * 初始化ChatGPT上下文的方法
     *
     * @param uid    用户ID，用于标识当前用户
     * @param roomId 房间ID，用于标识当前对话房间
     * @return 返回一个初始化好的ChatGPTContext对象
     */
    public static ChatGPTContext initContext(Long uid, Long roomId) {
        ChatGPTContext chatGPTContext = new ChatGPTContext();
        chatGPTContext.setUid(uid);
        chatGPTContext.setRoomId(roomId);
        chatGPTContext.addMsg(ChatGPTMsgBuilder.systemPrompt());
        return chatGPTContext;
    }

}
