package com.weiguang.modules.user.controller;

import com.weiguang.common.result.IdVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.user.dto.AddressRequest;
import com.weiguang.modules.user.dto.AddressVO;
import com.weiguang.modules.user.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地址簿接口（详设 §3.2 B1–B5）。
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /** B1 列表 */
    @GetMapping
    public Result<List<AddressVO>> list() {
        return Result.ok(addressService.list(AuthContext.requireUid()));
    }

    /** B2 新增 */
    @PostMapping
    public Result<IdVO> create(@Valid @RequestBody AddressRequest request) {
        return Result.ok(IdVO.of(addressService.create(AuthContext.requireUid(), request)));
    }

    /** B3 修改 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        addressService.update(AuthContext.requireUid(), id, request);
        return Result.ok();
    }

    /** B4 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(AuthContext.requireUid(), id);
        return Result.ok();
    }

    /** B5 设默认 */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(AuthContext.requireUid(), id);
        return Result.ok();
    }
}
