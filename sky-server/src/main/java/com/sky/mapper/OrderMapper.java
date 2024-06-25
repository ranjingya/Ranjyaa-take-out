package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.DailyTurnover;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    /**
     * 用户下单
     *
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     *
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     *
     * @param orders
     */
    void update(Orders orders);

    /**
     * 历史订单分页查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据订单id查询订单
     *
     * @param id
     * @return
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 各个状态的订单数量统计
     *
     * @param status
     * @return
     */
    @Select("select count(*) from orders where status = #{status}")
    Integer getStatus(Integer status);

    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    /**
     * 日期区间营业额统计
     *
     * @param begin
     * @param end
     * @param status
     * @return
     */
    List<DailyTurnover> sum(LocalDate begin, LocalDate end, Integer status);

    /**
     * 日期区间营业额统计(map)
     *
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 订单统计
     *
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 统计指定时间区间销量排名前十
     *
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin, LocalDateTime end);

}
