package com.demo.aiknowledge.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateUserRequest {
    private Long userId;
    private String username;
    private String password;
    private String avatarUrl;
    private Integer gender;
    private String ageRange;
    private String skinType;
    private List<String> preferenceTags;
}
