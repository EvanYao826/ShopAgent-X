package com.demo.aiknowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product_faq")
public class ProductFaq {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String question;
    private String answer;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
