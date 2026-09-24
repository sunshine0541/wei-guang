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
    private Integer isDefault;

    public static AddressVO from(AddressBook address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setLabel(address.getLabel());
        vo.setAreaText(address.getAreaText());
        vo.setDetailText(address.getDetailText());
        vo.setIsDefault(address.getIsDefault());
        return vo;
    }
}
