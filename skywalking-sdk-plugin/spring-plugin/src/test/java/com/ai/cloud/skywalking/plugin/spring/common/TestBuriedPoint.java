package com.ai.cloud.skywalking.plugin.spring.common;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestBuriedPoint {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:springConfig-common.xml");
        CallChainA callChainA = classPathXmlApplicationContext.getBean(CallChainA.class);
        callChainA.doBusiness();
    }
}
