package com.ai.cloud.skywalking.example.resource.dubbo.impl;

import org.springframework.beans.factory.annotation.Autowired;

import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.IResourceCheck;
import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.param.ResourceInfo;
import com.ai.cloud.skywalking.example.resource.service.IResourceManage;
import com.alibaba.dubbo.config.annotation.Service;

@Service
public class ResourceCheckImpl implements IResourceCheck {

    @Autowired
    private IResourceManage resourceManage;

    @Override
    public boolean checkResource(ResourceInfo info) {
        return resourceManage.checkResource(info);
    }

    public boolean reservationResource(ResourceInfo info) {
        return resourceManage.reservationResource(info);
    }
}
