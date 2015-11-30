package com.ai.cloud.skywalking.example.resource.dubbo.impl;

import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.IResourceCheck;
import com.ai.cloud.skywalking.example.resource.dubbo.interfaces.checker.param.ResourceInfo;
import com.ai.cloud.skywalking.example.resource.service.IResourceManage;
import com.ai.cloud.skywalking.plugin.spring.Tracing;
import com.alibaba.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ResourceCheckImpl implements IResourceCheck {

    @Autowired
    private IResourceManage resourceManage;

    @Override
    @Tracing
    public boolean checkResource(ResourceInfo info) {
        return resourceManage.checkResource(info);
    }

    @Tracing
    public boolean reservationResource(ResourceInfo info) {
        return resourceManage.reservationResource(info);
    }
}
