package com.ai.cloud.skywalking.plugin.test.dubbox.rest.impl;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DubboxRestStart {

    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext("classpath*:provider/dubbox-provider.xml");

        classPathXmlApplicationContext.start();

        while (true) {
            Thread.sleep(100000L);
        }
    }
}
