package com.a.eye.skywalking.plugin.test.dubbox283.impl;

import com.a.eye.skywalking.plugin.test.dubbox283.interfaces.IDubboxRestInterA;
import com.a.eye.skywalking.plugin.test.dubbox283.interfaces.param.DubboxRestInterAParameter;
import com.alibaba.dubbo.config.annotation.Service;

@Service
public class DubboxRestInterAImpl implements IDubboxRestInterA {
    public String doBusiness(DubboxRestInterAParameter paramA) {
        System.out.println("param : " + paramA.getParameterA());
        return "{\"content\":\"" + paramA.getParameterA() + "\"}";
    }
}
