package com.abin.mallchat.common.user.service.handler;

import com.abin.mallchat.common.user.service.WxMsgService;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.common.session.WxSessionManager;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Description: 扫码事件处理
 */
@Component
public class ScanHandler extends AbstractHandler {


    @Autowired
    private WxMsgService wxMsgService;

    /**
     * 处理扫码事件
     *
     * @param wxMpXmlMessage   // 微信消息对象
     * @param map              // 事件参数
     * @param wxMpService      // 微信服务对象
     * @param wxSessionManager // 会话管理器
     * @return
     * @throws WxErrorException
     */
    @Override
    public WxMpXmlOutMessage handle(WxMpXmlMessage wxMpXmlMessage, Map<String, Object> map,
                                    WxMpService wxMpService, WxSessionManager wxSessionManager) throws WxErrorException {
        // 扫码事件处理
        return wxMsgService.scan(wxMpService, wxMpXmlMessage);


    }

}
