package com.demo.aiknowledge.service;

import com.demo.aiknowledge.entity.Address;
import java.util.List;

public interface AddressService {
    List<Address> listByUserId(Long userId);
    Address addAddress(Address address);
    Address updateAddress(Address address);
    void deleteAddress(Long id, Long userId);
    void setDefault(Long id, Long userId);
}