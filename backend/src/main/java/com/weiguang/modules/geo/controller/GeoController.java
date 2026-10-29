package com.weiguang.modules.geo.controller;

import com.weiguang.common.annotation.RateLimit;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.geo.dto.GeoPoiVO;
import com.weiguang.modules.geo.service.GeoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地址搜索接口（4D/4D-补）：登录用户可调用，后端代理高德 POI 搜索与输入提示。
 */
@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final GeoService geoService;

    public GeoController(GeoService geoService) {
        this.geoService = geoService;
    }

    /** GET /api/geo/pois?keyword=梧桐苑 */
    @GetMapping("/pois")
    @RateLimit(prefix = "rate:geo:", limitKey = "rate.chat_per_minute")
    public Result<List<GeoPoiVO>> pois(@RequestParam(required = false) String keyword) {
        return Result.ok(geoService.search(AuthContext.requireUid(), keyword));
    }

    /** GET /api/geo/tips?keyword=梧桐 — 输入提示（无结果返回空列表） */
    @GetMapping("/tips")
    @RateLimit(prefix = "rate:geo:", limitKey = "rate.chat_per_minute")
    public Result<List<GeoPoiVO>> tips(@RequestParam(required = false) String keyword) {
        return Result.ok(geoService.tips(AuthContext.requireUid(), keyword));
    }
}
