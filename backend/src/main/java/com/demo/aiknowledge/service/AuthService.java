package com.demo.aiknowledge.service;

import com.demo.aiknowledge.dto.UpdateUserRequest;
import com.demo.aiknowledge.entity.User;

import java.util.Map;

public interface AuthService {
    void sendSmsCode(String phone);
    Map<String, Object> register(String phone, String code, String password, String username);
    Map<String, Object> login(String phone, String password);
    User updateUserInfo(UpdateUserRequest request);
    Map<String, Object> refreshToken(String token);
}
