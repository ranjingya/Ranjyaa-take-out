package com.sky.service.impl;

import com.sky.entity.DailyTurnover;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {


    @Autowired
    private OrderMapper orderMapper;

    /**
     * 统计指定时间区间内的营业额数据
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnOverStatistics(LocalDate begin, LocalDate end) {

        // date， turnover
        List<DailyTurnover> dailyTurnovers = orderMapper.sumByMap(begin, end, Orders.COMPLETED);

        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (begin.isBefore(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 用得到的日期范围列表创建日期营业额映射表，初始值0
        Map<LocalDate, Double> turnoverMap = new TreeMap<>(dateList // map实现用treemap，保证值按日期顺序排序
                .stream()
                .collect(Collectors.toMap(date -> date, date -> 0.0)));

        // 遍历查询结果，如果有数据则更新映射
        dailyTurnovers.forEach(dt -> {
            turnoverMap.put(dt.getDate(), dt.getTurnover());
        });

        List<Double> turnoverList = new ArrayList<>(turnoverMap.values());

        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }
}
