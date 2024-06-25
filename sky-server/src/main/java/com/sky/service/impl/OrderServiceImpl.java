package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class OrderServiceImpl implements OrderService {

    public static final String GEOCODING = "https://api.map.baidu.com/geocoding/v3";
    public static final String DIRECTION_LITE = "https://api.map.baidu.com/directionlite/v1";

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.shop.shop-coordinate}")
    private String shopCoordinate;
    @Value("${sky.baidu.ak}")
    private String ak;


    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        // 处理业务异常（地址簿为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        String address = addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail();

        // 检查是否超出配送范围
        checkOutOfRange(address);

        // 查询当前用户购物车数据
        Long userId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list == null || list.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 向订单表插入一条数据
        Orders orders = Orders.builder()
                .orderTime(LocalDateTime.now())
                .address(address)
                .payStatus(Orders.UN_PAID)
                .status(Orders.PENDING_PAYMENT)
                .number(String.valueOf(System.currentTimeMillis()))
                .phone(addressBook.getPhone())
                .consignee(addressBook.getConsignee())
                .userId(userId)
                .userName(userMapper.getById(userId).getName())
//                .deliveryStatus(ordersSubmitDTO.getDeliveryStatus())  // 前端就没传过来值
                .build();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        // 向订单明细表插入n条数据
        for (ShoppingCart cart : list) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId()); // 订单id（插入订单返回的主键值）
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        // 清空用户购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 返回VO结果
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

/*
        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }
*/

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        // 跳过微信支付，直接设置订单状态
        paySuccess(String.valueOf(ordersPaymentDTO.getOrderNumber())); // 订单号在数据库里就是字符串类型。。。

        return vo;

    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 历史订单分页查询
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQuery4User(Integer pageNum, Integer pageSize, Integer status) {

        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = OrdersPageQueryDTO.builder()
                .userId(BaseContext.getCurrentId())
                .status(status)
                .build();

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);  // Page本身就是列表
        List<OrderVO> voList = new ArrayList<>();

        if (page != null && !page.isEmpty()) {
            for (Orders orders : page) {

                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                OrderVO vo = new OrderVO();
                BeanUtils.copyProperties(orders, vo);
                vo.setOrderDetailList(orderDetailList); // vo中的orderDishes不知道有啥用，也没赋值
                voList.add(vo);
            }
        }
        return new PageResult(page.getTotal(), voList);
    }

    /**
     * 订单详情查询
     *
     * @param id 订单id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        OrderVO orderVO = new OrderVO();
        Orders orders = orderMapper.getById(id);
        BeanUtils.copyProperties(orders, orderVO);
        List<OrderDetail> list = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(list);
        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    @Override
    public void userCancel(Long id) {

        // 查询订单，判断订单状态
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 其他抛异常（与商家联系）
        if (orders.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 待接单需退款
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {

/*            //调用微信支付退款接口
            weChatPayUtil.refund(
                    ordersDB.getNumber(), //商户订单号
                    ordersDB.getNumber(), //商户退款单号
                    new BigDecimal(0.01),//退款金额，单位 元
                    new BigDecimal(0.01));//原订单金额
*/
            orders.setPayStatus(Orders.REFUND);
        }

        // 待付款直接取消
        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     *
     * @param id
     */
    @Override
    public void repetition(Long id) {

        List<OrderDetail> orderList = orderDetailMapper.getByOrderId(id);
        List<ShoppingCart> shoppingCartList = new ArrayList<>();

        for (OrderDetail orderDetail : orderList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCartList.add(shoppingCart);
        }

        shoppingCartMapper.insertBatch(shoppingCartList);

    }

    /**
     * 订单分页条件搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> orderVOList = new ArrayList<>();

        if (page != null && !page.isEmpty()) {
            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                // 还有菜品字符串
                orderVO.setOrderDishes(getDishStr(orders));
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        Integer toBeConfirmed = orderMapper.getStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.getStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.getStatus(Orders.DELIVERY_IN_PROGRESS);

        return OrderStatisticsVO.builder()
                .toBeConfirmed(toBeConfirmed)
                .confirmed(confirmed)
                .deliveryInProgress(deliveryInProgress)
                .build();
    }

    /**
     * 接单
     *
     * @param ordersDTO
     */
    @Override
    public void confirm(OrdersDTO ordersDTO) {
        Orders orders = Orders.builder()
                .id(ordersDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {

        Long id = ordersRejectionDTO.getId();
        Orders order = orderMapper.getById(id);
        // 状态为待接单才可以设置成拒单
        if (order != null && !order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 已付款的要退款
        if (order.getPayStatus().equals(Orders.PAID)) {
            log.info("退款：{}", order.getNumber());
        }

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelReason(ordersRejectionDTO.getRejectionReason())
                .payStatus(Orders.REFUND)
                .cancelTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 管理端取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        Long id = ordersCancelDTO.getId();
        Orders order = orderMapper.getById(id);

        // 已付款的退款
        if (order.getPayStatus().equals(Orders.PAID)) {
            log.info("退款：{}", order.getNumber());
        }
        Orders orders = Orders.builder()
                .id(id)
                .payStatus(Orders.REFUND)
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .cancelReason(ordersCancelDTO.getCancelReason())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 订单派送
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Orders order = orderMapper.getById(id);
        if (order != null && !order.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders order = orderMapper.getById(id);
        if (order != null && !order.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 获取订单详情中所有菜品并拼接
     *
     * @param orders
     * @return
     */
    private String getDishStr(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        return orderDetailList
                .stream()
                .map(dish -> dish.getName() + "*" + dish.getNumber())
                .collect(Collectors.joining(";\n"));


    }

    /**
     * 检测用户收货地址是否超出范围
     *
     * @param address 详细收货地址
     */
    private void checkOutOfRange(String address) {

        Map<String, String> map = new HashMap<>();
        map.put("output", "json");
        map.put("ak", ak);

/*
        // 解析店铺地址
        map.put("address", shopAddress);
        String shopCoordinate = HttpClientUtil.doGet(GEOCODING, map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException(MessageConstant.SHOP_ADDRESS_DECODE_FAILED);
        }

        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        String shopLngLat = lng + "," + lat;
*/

        // 解析用户地址
        map.put("address", address);
        String userCoordinateJson = HttpClientUtil.doGet(GEOCODING, map);

        JSONObject jsonObject = JSON.parseObject(userCoordinateJson);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException(MessageConstant.USER_ADDRESS_DECODE_FAILED);
        }

        JSONObject result = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = result.getString("lat");
        String lng = result.getString("lng");
        String userLngLat = lng + "," + lat;

        // 路线规划，获取路线总长度
        map.put("origin", shopCoordinate);
        map.put("destination", userLngLat);
        map.put("steps_info", "0");
        String planJson = HttpClientUtil.doGet(DIRECTION_LITE, map);
        jsonObject = JSON.parseObject(planJson);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException(MessageConstant.DELIVERY_ROUTE_PLAN_FAILED);
        }
        result = jsonObject.getJSONObject("result");
        ArrayList<Object> routesArr = (ArrayList<Object>) result.get("routes");
        Integer distance = (Integer) routesArr.get(0);

        if (distance > 5000) {
            throw new OrderBusinessException(MessageConstant.OUT_OF_DELIVERY_RANGE);
        }
    }
}
