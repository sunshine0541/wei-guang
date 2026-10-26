package com.weiguang.modules.geo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.modules.geo.dto.GeoPoiVO;
import com.weiguang.modules.user.entity.AddressBook;
import com.weiguang.modules.user.mapper.AddressBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 4D-补：地址搜索编排。
 * 用本人默认地址的坐标反查城市并缓存（命中 24h、未命中 10min），搜索/提示时带上城市，减少异地同名结果。
 */
@Slf4j
@Service
public class GeoService {

    private static final String CITY_KEY_PREFIX = "geo:city:";
    private static final String CITY_MISS = "none";
    private static final Duration CITY_TTL = Duration.ofHours(24);
    private static final Duration CITY_MISS_TTL = Duration.ofMinutes(10);

    private final AmapGeoService amapGeoService;
    private final AddressBookMapper addressBookMapper;
    private final StringRedisTemplate redisTemplate;

    public GeoService(AmapGeoService amapGeoService,
                      AddressBookMapper addressBookMapper,
                      StringRedisTemplate redisTemplate) {
        this.amapGeoService = amapGeoService;
        this.addressBookMapper = addressBookMapper;
        this.redisTemplate = redisTemplate;
    }

    public List<GeoPoiVO> search(Long uid, String keyword) {
        return amapGeoService.search(keyword, cityFor(uid));
    }

    public List<GeoPoiVO> tips(Long uid, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        return amapGeoService.tips(keyword, cityFor(uid));
    }

    /** 文字地址 → 坐标；调用方需容忍失败（失败返回 null 或抛业务异常） */
    public GeoPoiVO geocode(Long uid, String address) {
        return amapGeoService.geocode(address, cityFor(uid));
    }

    /** 默认地址所在城市；未定位、无默认或高德失败时返回 null（即不限城市） */
    public String cityFor(Long uid) {
        AddressBook address = defaultAddress(uid);
        if (address == null || address.getLatitude() == null || address.getLongitude() == null) {
            return null;
        }
        String key = CITY_KEY_PREFIX + String.format(Locale.ROOT, "%.3f,%.3f",
                address.getLatitude(), address.getLongitude());
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return CITY_MISS.equals(cached) ? null : cached;
            }
        } catch (Exception e) {
            log.warn("读取城市缓存失败：{}", e.getMessage());
        }
        try {
            String city = amapGeoService.cityOf(address.getLatitude(), address.getLongitude());
            boolean found = city != null && !city.isBlank();
            cacheCity(key, found ? city : CITY_MISS, found ? CITY_TTL : CITY_MISS_TTL);
            return found ? city : null;
        } catch (RuntimeException e) {
            log.warn("默认地址反查城市失败，本次不限城市：{}", e.getMessage());
            return null;
        }
    }

    private void cacheCity(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("写入城市缓存失败：{}", e.getMessage());
        }
    }

    private AddressBook defaultAddress(Long uid) {
        AddressBook def = addressBookMapper.selectOne(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getUserId, uid)
                .eq(AddressBook::getIsDefault, 1)
                .last("LIMIT 1"));
        if (def != null) {
            return def;
        }
        return addressBookMapper.selectOne(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getUserId, uid)
                .orderByAsc(AddressBook::getId)
                .last("LIMIT 1"));
    }
}
