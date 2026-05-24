package com.demo.aiknowledge.controller;

import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Address;
import com.demo.aiknowledge.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/list")
    public Result<List<Address>> list() {
        return Result.success(addressService.listByUserId(getCurrentUserId()));
    }

    @PostMapping("/add")
    public Result<Address> add(@RequestBody Address address) {
        address.setUserId(getCurrentUserId());
        return Result.success(addressService.addAddress(address));
    }

    @PutMapping("/update")
    public Result<Address> update(@RequestBody Address address) {
        address.setUserId(getCurrentUserId());
        return Result.success(addressService.updateAddress(address));
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        addressService.deleteAddress(id, getCurrentUserId());
        return Result.success(null);
    }

    @PutMapping("/setDefault")
    public Result<Void> setDefault(@RequestParam Long id) {
        addressService.setDefault(id, getCurrentUserId());
        return Result.success(null);
    }
}
