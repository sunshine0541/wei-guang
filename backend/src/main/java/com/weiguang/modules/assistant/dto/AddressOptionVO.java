package com.weiguang.modules.assistant.dto;

import com.weiguang.modules.user.dto.AddressVO;
import lombok.Data;

/**
 * 助手地址选择项（只给用户自己的地址簿，不暴露坐标）。
 */
@Data
public class AddressOptionVO {

    private Long id;
    private String label;
    private Integer isDefault;

    public static AddressOptionVO from(AddressVO address) {
        AddressOptionVO vo = new AddressOptionVO();
        vo.setId(address.getId());
        vo.setLabel((address.getLabel() == null ? "地址" : address.getLabel())
                + "（" + (address.getAreaText() == null ? "" : address.getAreaText()) + "）");
        vo.setIsDefault(address.getIsDefault());
        return vo;
    }
}
