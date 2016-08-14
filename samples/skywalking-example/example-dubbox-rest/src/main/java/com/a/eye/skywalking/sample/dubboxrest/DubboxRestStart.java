package com.a.eye.skywalking.sample.dubboxrest;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DubboxRestStart {

    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext classPathXmlApplicationContext =
                new ClassPathXmlApplicationContext("classpath*:spring-context.xml");
        classPathXmlApplicationContext.start();

        while (true) {
            Thread.sleep(100000L);
        }
    }
}
