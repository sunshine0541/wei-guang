package com.weiguang.modules.template.dto;

import com.weiguang.modules.template.entity.HelpTemplate;
import lombok.Data;

/**
 * T1 求助模板列表项（详设 §3.6：id/name/icon/coverDesc/defaultType/title）。
 */
@Data
public class TemplateVO {

    private Long id;
    private String name;
    private String icon;
    private String coverDesc;
    private String defaultType;
    private String title;

    public static TemplateVO from(HelpTemplate template) {
        TemplateVO vo = new TemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setIcon(template.getIcon());
        vo.setCoverDesc(template.getCoverDesc());
        vo.setDefaultType(template.getDefaultType());
        vo.setTitle(template.getTitle());
        return vo;
    }
}
