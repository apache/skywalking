package com.ai.cloud.skywalking.example.order.impl;

import com.ai.cloud.skywalking.example.account.dubbo.interfaces.IAccountMaintain;
import com.ai.cloud.skywalking.example.account.dubbo.interfaces.param.AccountInfo;
import com.ai.cloud.skywalking.example.order.exception.BusinessException;
import com.ai.cloud.skywalking.example.order.interfaces.IOrderMaintain;
import com.ai.cloud.skywalking.example.order.interfaces.parameter.OrderInfo;
import com.ai.cloud.skywalking.example.order.model.CommonsHttpResult;
import com.ai.cloud.skywalking.example.order.service.IOrderManage;
import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.IResourceCheck;
import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.param.ResourceInfo;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OrderMaintainImpl implements IOrderMaintain {
    @Reference
    private IResourceCheck iResourceCheck;

    @Autowired
    private IOrderManage orderManage;

    @Autowired
    private IAccountMaintain iAccountMaintain;


    @Override
    public String saveOrder(OrderInfo orderInfo) {
        ResourceInfo resourceInfo = new ResourceInfo();
        resourceInfo.setPhoneNumber(orderInfo.getPhoneNumber());
        resourceInfo.setResourceId(orderInfo.getResourceId());
        resourceInfo.setPhonePackage(orderInfo.getPackageId());
        boolean checkResourceResult = iResourceCheck.checkResource(resourceInfo);

        if (checkResourceResult) {
            // 预占资源
            iResourceCheck.reservationResource(resourceInfo);
            // 创建用户
            AccountInfo accountInfo = new AccountInfo();
            accountInfo.setPhoneNumber(orderInfo.getPhoneNumber());
            accountInfo.setMail(orderInfo.getMailAccount());
            String result = iAccountMaintain.create(accountInfo);
            CommonsHttpResult commonsHttpResult = new Gson().fromJson(result, CommonsHttpResult.class);
            if ("99999".equals(commonsHttpResult)) {
                throw new RuntimeException("Create account failed");
            }
            return orderManage.saveOrder(orderInfo);
        } else {
            throw new BusinessException("Resource check failed, please mail to Administrator");
        }
    }
}
