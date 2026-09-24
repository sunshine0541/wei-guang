package com.weiguang.modules.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.constants.ProfileConstants;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.user.dto.AddressRequest;
import com.weiguang.modules.user.dto.AddressVO;
import com.weiguang.modules.user.entity.AddressBook;
import com.weiguang.modules.user.mapper.AddressBookMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 地址簿（详设 §3.6 B1–B5）：仅本人可读写，每用户 ≤10 条，默认地址至多 1 条由应用层保证。
 */
@Service
public class AddressService {

    private final AddressBookMapper addressBookMapper;

    public AddressService(AddressBookMapper addressBookMapper) {
        this.addressBookMapper = addressBookMapper;
    }

    /** B1 列表（默认地址排首） */
    public List<AddressVO> list(Long uid) {
        List<AddressBook> addresses = addressBookMapper.selectList(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getUserId, uid)
                .orderByDesc(AddressBook::getIsDefault)
                .orderByAsc(AddressBook::getId));
        return addresses.stream().map(AddressVO::from).toList();
    }

    /** B2 新增（上限 10 条；isDefault=1 时同事务清其他默认） */
    @Transactional
    public Long create(Long uid, AddressRequest request) {
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
        address.setIsDefault(toDefault ? 1 : 0);
        addressBookMapper.insert(address);
        return address.getId();
    }

    /** B3 修改（归属校验） */
    @Transactional
    public void update(Long uid, Long id, AddressRequest request) {
        requireOwned(uid, id);
        boolean toDefault = isDefault(request);
        if (toDefault) {
            clearDefault(uid);
        }
        addressBookMapper.update(null, Wrappers.<AddressBook>lambdaUpdate()
                .eq(AddressBook::getId, id)
                .set(AddressBook::getLabel, request.getLabel())
                .set(AddressBook::getAreaText, request.getAreaText())
                .set(AddressBook::getDetailText, request.getDetailText())
                .set(AddressBook::getIsDefault, toDefault ? 1 : 0));
    }

    /** B4 删除（逻辑删除，幂等） */
    public void delete(Long uid, Long id) {
        requireOwned(uid, id);
        addressBookMapper.deleteById(id);
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
