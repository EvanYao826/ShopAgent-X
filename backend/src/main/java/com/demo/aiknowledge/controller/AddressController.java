package com.demo.aiknowledge.controller;

import com.demo.aiknowledge.common.Result;
import com.demo.aiknowledge.entity.Address;
import com.demo.aiknowledge.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/list")
    public Result<List<Address>> list(@RequestParam Long userId) {
        return Result.success(addressService.listByUserId(userId));
    }

    @PostMapping("/add")
    public Result<Address> add(@RequestBody Address address) {
        return Result.success(addressService.addAddress(address));
    }

    @PutMapping("/update")
    public Result<Address> update(@RequestBody Address address) {
        return Result.success(addressService.updateAddress(address));
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Long id, @RequestParam Long userId) {
        addressService.deleteAddress(id, userId);
        return Result.success(null);
    }

    @PutMapping("/setDefault")
    public Result<Void> setDefault(@RequestParam Long id, @RequestParam Long userId) {
        addressService.setDefault(id, userId);
        return Result.success(null);
    }
}