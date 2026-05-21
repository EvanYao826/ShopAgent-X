package com.demo.aiknowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productCode;
    private Long categoryId;
    private String title;
    private String brand;
    private String subCategory;
    private BigDecimal basePrice;
    private String imageUrl;
    private String description;
    private String tags;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer salesCount;
    private Integer status;
    private Integer embeddingStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
