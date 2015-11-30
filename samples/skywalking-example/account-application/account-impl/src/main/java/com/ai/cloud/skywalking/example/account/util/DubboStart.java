package com.ai.cloud.skywalking.example.account.util;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DubboStart {


    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext context =
                new ClassPathXmlApplicationContext("classpath*:applicationContext.xml");
        context.start();
        while (true){
            Thread.sleep(1000000000L);
        }
    }
}
