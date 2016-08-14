package com.a.eye.skywalking.plugin.test.dubbo.impl;

import com.a.eye.skywalking.plugin.test.dubbo.interfaces.IDubboInterA;
import com.alibaba.dubbo.config.annotation.Service;

@Service
public class DubboInterAImpl implements IDubboInterA {
    public void doBusiness(String paramA) {
        System.out.println("param : " + paramA);
    }
}
