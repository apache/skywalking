package com.ai.cloud.skywalking.plugin.test.dubbox284.impl;

import com.ai.cloud.skywalking.plugin.test.dubbox283.interfaces.param.DubboxRestInterAParameter;
import com.ai.cloud.skywalking.plugin.test.dubbox284.interfaces.IDubboxRestInterA;
import com.alibaba.dubbo.config.annotation.Service;

@Service
public class DubboxRestInterAImpl implements IDubboxRestInterA {
    public String doBusiness(DubboxRestInterAParameter paramA) {
        System.out.println("param : " + paramA.getParameterA());
        return "{\"content\":\"" + paramA.getParameterA() + "\"}";
    }
}
