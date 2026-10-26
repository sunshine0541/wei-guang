package com.weiguang.modules.user.dto;

import com.weiguang.modules.user.entity.AddressBook;
import lombok.Data;

/**
 * B1 地址簿列表项（详设 §3.6）。
 */
@Data
public class AddressVO {

    private Long id;
    private String label;
    private String areaText;
    private String detailText;
    /** POI 坐标：仅返回给本人地址簿，用于编辑回显与距离计算 */
    private Double latitude;
    private Double longitude;
    private Integer isDefault;

    public static AddressVO from(AddressBook address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setLabel(address.getLabel());
        vo.setAreaText(address.getAreaText());
        vo.setDetailText(address.getDetailText());
        vo.setLatitude(address.getLatitude());
        vo.setLongitude(address.getLongitude());
        vo.setIsDefault(address.getIsDefault());
        return vo;
    }
}
