package com.weiguang.modules.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.constants.ProfileConstants;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.geo.dto.GeoPoiVO;
import com.weiguang.modules.geo.service.GeoService;
import com.weiguang.modules.user.dto.AddressRequest;
import com.weiguang.modules.user.dto.AddressVO;
import com.weiguang.modules.user.entity.AddressBook;
import com.weiguang.modules.user.mapper.AddressBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 地址簿（详设 §3.6 B1–B5）：仅本人可读写，每用户 ≤10 条，默认地址至多 1 条由应用层保证。
 * 4D-补：手填地址未带坐标时，保存前用高德地理编码兜底解析（失败不阻断保存）。
 * 4D-补5：地理编码是外部调用，放在事务外执行，避免长时间占用数据库连接。
 */
@Slf4j
@Service
public class AddressService {

    private final AddressBookMapper addressBookMapper;
    private final GeoService geoService;
    private final TransactionTemplate transactionTemplate;

    public AddressService(AddressBookMapper addressBookMapper, GeoService geoService,
                          PlatformTransactionManager transactionManager) {
        this.addressBookMapper = addressBookMapper;
        this.geoService = geoService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** B1 列表（默认地址排首） */
    public List<AddressVO> list(Long uid) {
        List<AddressBook> addresses = addressBookMapper.selectList(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getUserId, uid)
                .orderByDesc(AddressBook::getIsDefault)
                .orderByAsc(AddressBook::getId));
        return addresses.stream().map(AddressVO::from).toList();
    }

    /** B2 新增（上限 10 条；isDefault=1 时同事务清其他默认；地理编码在事务外） */
    public Long create(Long uid, AddressRequest request) {
        requireGeoPair(request);
        fillGeo(uid, request);
        return transactionTemplate.execute(status -> doCreate(uid, request));
    }

    private Long doCreate(Long uid, AddressRequest request) {
        Long count = addressBookMapper.selectCount(
                Wrappers.<AddressBook>lambdaQuery().eq(AddressBook::getUserId, uid));
        if (count != null && count >= ProfileConstants.ADDRESS_MAX_COUNT) {
            throw new BizException(ErrorCode.ADDRESS_LIMIT);
        }
        boolean toDefault = isDefault(request);
        if (toDefault) {
            clearDefault(uid);
        }
        AddressBook address = new AddressBook();
        address.setUserId(uid);
        address.setLabel(request.getLabel());
        address.setAreaText(request.getAreaText());
        address.setDetailText(request.getDetailText());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setIsDefault(toDefault ? 1 : 0);
        addressBookMapper.insert(address);
        return address.getId();
    }

    /** B3 修改（归属校验；地理编码在事务外） */
    public void update(Long uid, Long id, AddressRequest request) {
        requireOwned(uid, id);
        requireGeoPair(request);
        fillGeo(uid, request);
        transactionTemplate.executeWithoutResult(status -> doUpdate(uid, id, request));
    }

    private void doUpdate(Long uid, Long id, AddressRequest request) {
        boolean toDefault = isDefault(request);
        if (toDefault) {
            clearDefault(uid);
        }
        addressBookMapper.update(null, Wrappers.<AddressBook>lambdaUpdate()
                .eq(AddressBook::getId, id)
                .set(AddressBook::getLabel, request.getLabel())
                .set(AddressBook::getAreaText, request.getAreaText())
                .set(AddressBook::getDetailText, request.getDetailText())
                .set(AddressBook::getLatitude, request.getLatitude())
                .set(AddressBook::getLongitude, request.getLongitude())
                .set(AddressBook::getIsDefault, toDefault ? 1 : 0));
        // 4D-补：地址簿始终保留一条默认，编辑时误取消则自动补位
        if (!toDefault) {
            ensureDefault(uid);
        }
    }

    /** B4 删除（逻辑删除，幂等；4D-补：删掉默认后自动补位） */
    @Transactional
    public void delete(Long uid, Long id) {
        AddressBook address = requireOwned(uid, id);
        addressBookMapper.deleteById(id);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            ensureDefault(uid);
        }
    }

    /** B5 设默认（事务：清旧默认 + 设新默认） */
    @Transactional
    public void setDefault(Long uid, Long id) {
        requireOwned(uid, id);
        clearDefault(uid);
        addressBookMapper.update(null, Wrappers.<AddressBook>lambdaUpdate()
                .eq(AddressBook::getId, id)
                .set(AddressBook::getIsDefault, 1));
    }

    /** 4D-补：有地址但零默认时，把最早一条补为默认 */
    private void ensureDefault(Long uid) {
        Long defaults = addressBookMapper.selectCount(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getUserId, uid)
                .eq(AddressBook::getIsDefault, 1));
        if (defaults != null && defaults > 0) {
            return;
        }
        AddressBook earliest = addressBookMapper.selectOne(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getUserId, uid)
                .orderByAsc(AddressBook::getId)
                .last("LIMIT 1"));
        if (earliest != null) {
            addressBookMapper.update(null, Wrappers.<AddressBook>lambdaUpdate()
                    .eq(AddressBook::getId, earliest.getId())
                    .set(AddressBook::getIsDefault, 1));
        }
    }

    /** 4D-补：经纬度必须成对出现 */
    private void requireGeoPair(AddressRequest request) {
        if ((request.getLatitude() == null) != (request.getLongitude() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
    }

    /** 4D-补：没点地图、手填的小区/区域尝试地理编码补坐标；失败按无坐标保存，不阻断 */
    private void fillGeo(Long uid, AddressRequest request) {
        if (request.getLatitude() != null && request.getLongitude() != null) {
            return;
        }
        String area = request.getAreaText();
        if (area == null || area.isBlank()) {
            return;
        }
        try {
            GeoPoiVO poi = geoService.geocode(uid, area.trim());
            if (poi != null && poi.getLatitude() != null && poi.getLongitude() != null) {
                request.setLatitude(poi.getLatitude());
                request.setLongitude(poi.getLongitude());
            }
        } catch (RuntimeException e) {
            log.warn("地址自动解析坐标失败，按无坐标保存：{}", e.getMessage());
        }
    }

    private void clearDefault(Long uid) {
        addressBookMapper.update(null, Wrappers.<AddressBook>lambdaUpdate()
                .eq(AddressBook::getUserId, uid)
                .eq(AddressBook::getIsDefault, 1)
                .set(AddressBook::getIsDefault, 0));
    }

    private AddressBook requireOwned(Long uid, Long id) {
        AddressBook address = addressBookMapper.selectOne(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getId, id)
                .eq(AddressBook::getUserId, uid));
        if (address == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return address;
    }

    private boolean isDefault(AddressRequest request) {
        return request.getIsDefault() != null && request.getIsDefault() == 1;
    }
}
