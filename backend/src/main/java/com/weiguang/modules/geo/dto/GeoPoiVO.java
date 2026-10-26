package com.weiguang.modules.geo.dto;

import lombok.Data;

/**
 * 高德 POI 搜索结果（4D 地址导入）：前端选点后把 name 填入小区/区域，并带回经纬度。
 */
@Data
public class GeoPoiVO {

    /** POI 名称（如「梧桐苑小区」） */
    private String name;

    /** 详细地址（展示用） */
    private String address;

    /** 所属区县（展示用） */
    private String area;

    /** 纬度（gcj02） */
    private Double latitude;

    /** 经度（gcj02） */
    private Double longitude;
}
