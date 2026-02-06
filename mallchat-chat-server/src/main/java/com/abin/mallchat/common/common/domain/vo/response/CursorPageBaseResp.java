package com.abin.mallchat.common.common.domain.vo.response;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 游标翻页返回
 *
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @since 2023-03-19
 */
@Data
@ApiModel("游标翻页返回")
@AllArgsConstructor
@NoArgsConstructor
public class CursorPageBaseResp<T> {

    @ApiModelProperty("游标（下次翻页带上这参数）")
    private String cursor;

    @ApiModelProperty("是否最后一页")
    private Boolean isLast = Boolean.FALSE;

    @ApiModelProperty("数据列表")
    private List<T> list;

    /**
     * 构造方法，为了兼容旧版本，如果list为空，则返回一个空的CursorPageBaseResp
     *
     * @param cursorPage
     * @param list
     * @param <T>
     * @return
     */
    public static <T> CursorPageBaseResp<T> init(CursorPageBaseResp cursorPage, List<T> list) {
        CursorPageBaseResp<T> cursorPageBaseResp = new CursorPageBaseResp<T>();
        cursorPageBaseResp.setIsLast(cursorPage.getIsLast());
        cursorPageBaseResp.setList(list);
        cursorPageBaseResp.setCursor(cursorPage.getCursor());
        return cursorPageBaseResp;
    }

    /**
     * 判断是否为空
     *
     * @return
     * @JsonIgnore： 忽略字段，不会被序列化
     */
    @JsonIgnore
    public Boolean isEmpty() {
        return CollectionUtil.isEmpty(list);
    }

    /**
     * 创建一个空的CursorPageBaseResp，用于结束翻页，或者没有更多数据了
     *
     * @param <T>
     * @return
     */
    public static <T> CursorPageBaseResp<T> empty() {
        CursorPageBaseResp<T> cursorPageBaseResp = new CursorPageBaseResp<T>();
        cursorPageBaseResp.setIsLast(true);
        cursorPageBaseResp.setList(new ArrayList<T>());
        return cursorPageBaseResp;
    }

}
