package com.weiguang.modules.geo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.geo.dto.GeoPoiVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 高德 Web 服务封装（4D）：Key 只从后端 .env（AMAP_WEB_KEY）读取，前端不接触 Key。
 * 提供 POI 搜索、输入提示、地理编码与逆地理编码（取城市）；失败统一抛 50002 + 温柔文案。
 */
@Slf4j
@Service
public class AmapGeoService {

    private static final String ERROR_MESSAGE = "地址搜索暂时不可用，请手动填写";
    private static final int MAX_KEYWORD_LENGTH = 30;
    private static final int MAX_TIPS = 8;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String key;

    public AmapGeoService(ObjectMapper objectMapper, @Value("${AMAP_WEB_KEY:}") String key) {
        this.objectMapper = objectMapper;
        this.key = key;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** POI 关键字搜索（city 非空时优先返回该城市结果） */
    public List<GeoPoiVO> search(String keyword, String city) {
        String trimmed = requireKeyword(keyword);
        Map<String, String> params = baseParams(trimmed, city);
        params.put("offset", "10");
        params.put("page", "1");
        params.put("extensions", "base");
        JsonNode root = call("/v3/place/text", params);
        List<GeoPoiVO> result = new ArrayList<>();
        for (JsonNode poi : root.path("pois")) {
            GeoPoiVO vo = toPoi(poi);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    /** 输入提示（边输边联想；无坐标的提示会被丢弃） */
    public List<GeoPoiVO> tips(String keyword, String city) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Map<String, String> params = baseParams(trimmed, city);
        params.put("datatype", "poi");
        JsonNode root = call("/v3/assistant/inputtips", params);
        List<GeoPoiVO> result = new ArrayList<>();
        for (JsonNode tip : root.path("tips")) {
            double[] location = parseLocation(text(tip.path("location"), ""));
            if (location == null) {
                continue;
            }
            GeoPoiVO vo = new GeoPoiVO();
            vo.setName(text(tip.path("name"), ""));
            vo.setAddress(text(tip.path("address"), ""));
            vo.setArea(text(tip.path("district"), ""));
            vo.setLatitude(location[0]);
            vo.setLongitude(location[1]);
            result.add(vo);
            if (result.size() >= MAX_TIPS) {
                break;
            }
        }
        return result;
    }

    /** 地理编码：文字地址 → 坐标（无结果返回 null） */
    public GeoPoiVO geocode(String address, String city) {
        String trimmed = address == null ? "" : address.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 100) {
            return null;
        }
        Map<String, String> params = baseParams(trimmed, city);
        params.put("address", trimmed);
        params.remove("keywords");
        JsonNode root = call("/v3/geocode/geo", params);
        JsonNode geocodes = root.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            return null;
        }
        JsonNode first = geocodes.get(0);
        double[] location = parseLocation(text(first.path("location"), ""));
        if (location == null) {
            return null;
        }
        String formatted = text(first.path("formatted_address"), trimmed);
        GeoPoiVO vo = new GeoPoiVO();
        vo.setName(formatted);
        vo.setAddress(formatted);
        vo.setArea(text(first.path("district"), text(first.path("city"), "")));
        vo.setLatitude(location[0]);
        vo.setLongitude(location[1]);
        return vo;
    }

    /** 逆地理编码：坐标 → 城市名（直辖市无 city 时回退省份；无结果返回 null） */
    public String cityOf(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", requireKey());
        params.put("location", String.format(java.util.Locale.ROOT, "%.6f,%.6f", longitude, latitude));
        params.put("extensions", "base");
        JsonNode root = call("/v3/geocode/regeo", params);
        JsonNode component = root.path("regeocode").path("addressComponent");
        String city = text(component.path("city"), "");
        if (!city.isBlank()) {
            return city;
        }
        String province = text(component.path("province"), "");
        return province.isBlank() ? null : province;
    }

    private GeoPoiVO toPoi(JsonNode poi) {
        double[] location = parseLocation(text(poi.path("location"), ""));
        if (location == null) {
            return null;
        }
        GeoPoiVO vo = new GeoPoiVO();
        vo.setName(text(poi.path("name"), ""));
        vo.setAddress(text(poi.path("address"), ""));
        vo.setArea(text(poi.path("adname"), ""));
        vo.setLatitude(location[0]);
        vo.setLongitude(location[1]);
        return vo;
    }

    private Map<String, String> baseParams(String keyword, String city) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", requireKey());
        params.put("keywords", keyword);
        if (city != null && !city.isBlank()) {
            params.put("city", city);
        }
        return params;
    }

    private JsonNode call(String path, Map<String, String> params) {
        String raw;
        try {
            raw = restClient.get()
                    .uri(builder -> {
                        builder.scheme("https").host("restapi.amap.com").path(path);
                        params.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("高德 {} 调用异常：{}", path, e.getMessage());
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, ERROR_MESSAGE);
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("高德 {} 失败：info={} infocode={}", path,
                        root.path("info").asText(), root.path("infocode").asText());
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, ERROR_MESSAGE);
            }
            return root;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("高德 {} 响应解析失败：{}", path, e.getMessage());
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, ERROR_MESSAGE);
        }
    }

    private String requireKey() {
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, ERROR_MESSAGE);
        }
        return key;
    }

    private String requireKeyword(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return trimmed;
    }

    private String text(JsonNode node, String fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        if (node.isArray()) {
            return node.isEmpty() ? fallback : text(node.get(0), fallback);
        }
        String value = node.asText("");
        return value.isBlank() ? fallback : value;
    }

    /** 高德返回 "经度,纬度"，解析成 [纬度, 经度]；非法返回 null */
    private double[] parseLocation(String location) {
        if (location == null) {
            return null;
        }
        String[] parts = location.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double longitude = Double.parseDouble(parts[0].trim());
            double latitude = Double.parseDouble(parts[1].trim());
            if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                return null;
            }
            return new double[]{latitude, longitude};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
