package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.aiknowledge.entity.Address;
import com.demo.aiknowledge.mapper.AddressMapper;
import com.demo.aiknowledge.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    @Override
    public List<Address> listByUserId(Long userId) {
        return addressMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByDesc(Address::getCreateTime));
    }

    @Override
    @Transactional
    public Address addAddress(Address address) {
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(address.getUserId());
        }
        address.setCreateTime(LocalDateTime.now());
        addressMapper.insert(address);
        return address;
    }

    @Override
    @Transactional
    public Address updateAddress(Address address) {
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(address.getUserId());
        }
        addressMapper.updateById(address);
        return address;
    }

    @Override
    @Transactional
    public void deleteAddress(Long id, Long userId) {
        addressMapper.delete(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, id)
                        .eq(Address::getUserId, userId));
    }

    @Override
    @Transactional
    public void setDefault(Long id, Long userId) {
        clearDefault(userId);
        Address address = new Address();
        address.setId(id);
        address.setIsDefault(1);
        addressMapper.updateById(address);
    }

    private void clearDefault(Long userId) {
        Address update = new Address();
        update.setIsDefault(0);
        addressMapper.update(update,
                new LambdaUpdateWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, 1));
    }
}