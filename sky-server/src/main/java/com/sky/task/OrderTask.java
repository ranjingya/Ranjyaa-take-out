package com.sky.task;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单
     */
    @Scheduled(cron = "0 * * * * ?")    // 每分钟
    public void processTimeoutOrder(){
        LocalDateTime now = LocalDateTime.now();
        log.info("处理超时订单：{}", now);
        LocalDateTime time = now.minusMinutes(15);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);

        if(ordersList != null && !ordersList.isEmpty()){
            ordersList.forEach(orders -> {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason(MessageConstant.ORDER_PAYMENT_TIMEOUT);
                orders.setCancelTime(now);
                orderMapper.update(orders);
            });
        }
    }

    /**
     * 处理一直处于派送中的订单
     */
    @Scheduled(cron = "0 0 1 * * ?")    // 每天凌晨一点
    public void processDeliveryOrder(){
        LocalDateTime now = LocalDateTime.now();
        log.info("处理一直处于派送中的订单：{}", now);
        LocalDateTime time = now.minusHours(1);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        if(ordersList != null && !ordersList.isEmpty()){
            ordersList.forEach(orders -> {
                orders.setStatus(Orders.COMPLETED);
                orders.setDeliveryTime(now);
                orderMapper.update(orders);
            });
        }
    }
}
