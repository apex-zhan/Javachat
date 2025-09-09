package com.abin.mallchat.common.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 用户背包表
 * </p>
 *
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @Data // 生成getter、setter、toString等方法
 * @EqualsAndHashCode(callSuper = false) // 重写equals和hashCode方法，callSuper = false表示不调用父类的equals和hashCode方法
 * @Builder // 生成建造者模式，可以通过链式调用来创建对象
 * @AllArgsConstructor // 生成包含所有字段的构造器
 * @NoArgsConstructor // 生成无参构造器
 * @since 2023-03-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("user_backpack")
public class UserBackpack implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * uid, 用户id
     */
    @TableField("uid")
    private Long uid;

    /**
     * 物品id
     */
    @TableField("item_id")
    private Long itemId;

    /**
     * 使用状态 0.未失效 1失效
     */
    @TableField("status")
    private Integer status;

    /**
     * 幂等号
     */
    @TableField("idempotent")
    private String idempotent;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private Date updateTime;


}
