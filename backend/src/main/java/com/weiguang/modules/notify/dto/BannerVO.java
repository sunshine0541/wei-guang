package com.weiguang.modules.notify.dto;

import lombok.Data;

/**
 * N2 首页横幅（详设 §3.6）：{hasUnread, latest?, extraCount, bannerText}。
 * extraCount=未读−1，上限 notify.banner_max_extra，超出时值为 "{上限}+"（字符串）。
 */
@Data
public class BannerVO {

    private boolean hasUnread;
    private LatestVO latest;
    /** Integer（未超上限）或 String（"{上限}+"，超出时） */
    private Object extraCount;
    private String bannerText;
}