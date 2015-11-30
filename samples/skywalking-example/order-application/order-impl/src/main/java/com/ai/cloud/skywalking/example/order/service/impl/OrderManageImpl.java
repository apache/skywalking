package com.ai.cloud.skywalking.example.order.service.impl;

import com.ai.cloud.skywalking.example.order.dao.mapper.bo.TOrder;
import com.ai.cloud.skywalking.example.order.dao.mapper.interfaces.TOrderMapper;
import com.ai.cloud.skywalking.example.order.interfaces.parameter.OrderInfo;
import com.ai.cloud.skywalking.example.order.service.IOrderManage;
import com.ai.cloud.skywalking.example.order.util.OrderIdGenerator;
import com.ai.cloud.skywalking.plugin.spring.Tracing;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderManageImpl implements IOrderManage {

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Override
    @Tracing
    public String saveOrder(OrderInfo orderInfo) {
        String orderId = OrderIdGenerator.generate();
        TOrderMapper iOrderMapper = sqlSessionTemplate.getMapper(TOrderMapper.class);
        TOrder tOrder = new TOrder();
        tOrder.setPhoneNumber(orderInfo.getPhoneNumber());
        tOrder.setOrderId(orderId);
        tOrder.setPackageId(Integer.valueOf(orderInfo.getPackageId()));
        tOrder.setResourceId(Integer.valueOf(orderInfo.getResourceId()));
        tOrder.setMailAccount(orderInfo.getMailAccount());
        iOrderMapper.insert(tOrder);
        return orderId;
    }
}
