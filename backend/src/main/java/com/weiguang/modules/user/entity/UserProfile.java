package com.weiguang.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户画像（表 user_profile）。
 */
@Data
@TableName("user_profile")
public class UserProfile {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** FK->user.id */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 自主申报年龄；未填=普通模式 */
    private Integer age;

    /** 个性签名 */
    private String signature;

    /** 喜欢做的事，JSON 数组（8 项） */
    private String hobbies;

    /** 需要留意的小习惯 */
    private String careTip;

    /** 可提供帮助方向，JSON 数组 */
    private String helpDirections;

    /** 紧急联系人姓名 */
    private String emergencyContactName;

    /** 紧急联系人电话 */
    private String emergencyContactPhone;

    /** 选填联系方式，不参与鉴权 */
    private String phone;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
