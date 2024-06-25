package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 数据库日期范围查询结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyTurnover {
    private LocalDate date;
    private Double turnover;
}
