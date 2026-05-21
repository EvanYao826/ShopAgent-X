package com.demo.aiknowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "recommendation_log", autoResultMap = true)
public class RecommendationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sessionId;
    private Long messageId;
    private String query;
    private String intent;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> recommendedProductIds;
    private String recommendReason;
    private String agentReasoning;
    private Boolean userClicked;
    private Integer userFeedback;
    private LocalDateTime createTime;
}
