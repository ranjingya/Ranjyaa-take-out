package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.DailyTurnover;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {


    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

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
        List<DailyTurnover> dailyTurnovers = orderMapper.sum(begin, end, Orders.COMPLETED);

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

    /**
     * 统计指定时间区间内的用户数据
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (begin.isBefore(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }


        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();

        dateList.forEach(date -> {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap<>();
            map.put("end", endTime);
            Integer totalUser = userMapper.countByMap(map);
            map.put("begin", beginTime);
            Integer newUser = userMapper.countByMap(map);

            totalUserList.add(totalUser);
            newUserList.add(newUser);
        });

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 订单统计
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (begin.isBefore(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }


        List<Integer> totalOrderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();

        dateList.forEach(date -> {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            // 每天的订单数据
            Integer totalOrderCount = getOrderCount(beginTime, endTime, null);
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            totalOrderCountList.add(totalOrderCount);
            validOrderCountList.add(validOrderCount);
        });

        // 时间段内总的订单数据
        Integer totalOrderCount = totalOrderCountList.stream().reduce(Integer::sum).get();
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

//        Double orderCompleteRate = totalOrderCount.equals(0) ? 0.0 : validOrderCount.doubleValue() / totalOrderCount;

        Double orderCompleteRate = 0.0;
        if (!totalOrderCount.equals(0)) {
            orderCompleteRate = validOrderCount.doubleValue() / totalOrderCount;
        }


        return OrderReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(totalOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompleteRate)
                .build();
    }

    /**
     * 销量Top10
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        List<String> salesNameList = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String salesNameStr = StringUtils.join(salesNameList, ",");

        List<Integer> salesNumberList = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String salesNumberStr = StringUtils.join(salesNumberList, ",");

        log.info("top10：{},{}", salesNameStr, salesNumberStr);

        return SalesTop10ReportVO
                .builder()
                .nameList(salesNameStr)
                .numberList(salesNumberStr)
                .build();
    }

    /**
     * 导出运营数据报表
     *
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) throws IOException {

        // 查询数据库获取营业数据
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);

        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));

        // 通过POI将数据写入excel文件中

        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        XSSFWorkbook excel = new XSSFWorkbook(in);

        // 填充数据
        XSSFSheet sheet = excel.getSheetAt(0);
        // 时间
        sheet.getRow(1).getCell(1).setCellValue("时间：" + dateBegin + "至" + dateEnd);

        XSSFRow row = sheet.getRow(3);
        row.getCell(2).setCellValue(businessDataVO.getTurnover());  // 营业额
        row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());  // 订单完成率
        row.getCell(6).setCellValue(businessDataVO.getNewUsers());  // 新增用户

        row = sheet.getRow(4);
        row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());  // 有效订单数
        row.getCell(4).setCellValue(businessDataVO.getUnitPrice());  // 平均客单价

        // 填充明细数据
        for (int i = 0; i < 30; i++) {
            LocalDate date = dateBegin.plusDays(i);
            // 查询某一天的营业数据
            BusinessDataVO data = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
            row = sheet.getRow(7 + i);
            row.getCell(1).setCellValue(date.toString());
            row.getCell(2).setCellValue(data.getTurnover());
            row.getCell(3).setCellValue(data.getValidOrderCount());
            row.getCell(4).setCellValue(data.getOrderCompletionRate());
            row.getCell(5).setCellValue(data.getUnitPrice());
            row.getCell(6).setCellValue(data.getNewUsers());
        }

        // 通过输出流讲excel文件下载到客户端浏览器
        ServletOutputStream out = response.getOutputStream();
        excel.write(out);

        // 关闭资源
        excel.close();
        in.close();
        out.close();

    }


    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {
        Map map = new HashMap<>();
        map.put("end", end);
        map.put("begin", begin);
        map.put("status", status);
        return orderMapper.countByMap(map);
    }
}
