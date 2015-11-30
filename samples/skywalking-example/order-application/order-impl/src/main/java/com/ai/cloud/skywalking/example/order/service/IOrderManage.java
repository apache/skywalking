package com.ai.cloud.skywalking.example.order.service;

import com.ai.cloud.skywalking.example.order.interfaces.parameter.OrderInfo;

public interface IOrderManage {
    String saveOrder(OrderInfo orderInfo);
}
