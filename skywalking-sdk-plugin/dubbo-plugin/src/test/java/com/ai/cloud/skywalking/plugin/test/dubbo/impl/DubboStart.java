package com.ai.cloud.skywalking.plugin.test.dubbo.impl;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DubboStart {

    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext("classpath*:provider/dubbo-provider.xml");

        classPathXmlApplicationContext.start();

        while (true) {
            Thread.sleep(100000L);
        }
    }
}
