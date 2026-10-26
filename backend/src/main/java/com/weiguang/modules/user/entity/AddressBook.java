package com.weiguang.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常去地址簿（表 address_book）。
 */
@Data
@TableName("address_book")
public class AddressBook {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 标签：家/居委会/公园/超市等 */
    private String label;

    /** 小区/区域（接单后可见；陪同类即集合点） */
    private String areaText;

    /** 楼栋门牌（上门类确认开始后解锁） */
    private String detailText;

    /** POI 纬度（服务端距离计算，不下发） */
    private Double latitude;

    /** POI 经度（服务端距离计算，不下发） */
    private Double longitude;

    /** 默认地址，每用户至多 1 条 */
    private Integer isDefault;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
