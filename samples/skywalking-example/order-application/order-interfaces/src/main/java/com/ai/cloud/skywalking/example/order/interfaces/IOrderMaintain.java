package com.ai.cloud.skywalking.example.order.interfaces;

import com.ai.cloud.skywalking.example.order.interfaces.parameter.OrderInfo;

public interface IOrderMaintain {
    String saveOrder(OrderInfo orderInfo);
}
