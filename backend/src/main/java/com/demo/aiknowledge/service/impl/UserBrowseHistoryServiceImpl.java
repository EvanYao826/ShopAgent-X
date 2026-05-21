package com.demo.aiknowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.aiknowledge.entity.UserBrowseHistory;
import com.demo.aiknowledge.mapper.UserBrowseHistoryMapper;
import com.demo.aiknowledge.service.UserBrowseHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBrowseHistoryServiceImpl implements UserBrowseHistoryService {

    private final UserBrowseHistoryMapper userBrowseHistoryMapper;

    @Override
    public void save(UserBrowseHistory history) {
        userBrowseHistoryMapper.insert(history);
    }

    @Override
    public List<UserBrowseHistory> listByUserId(Long userId) {
        return userBrowseHistoryMapper.selectList(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .orderByDesc(UserBrowseHistory::getCreateTime));
    }
}
