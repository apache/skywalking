package com.ai.cloud.skywalking.plugin.test.dubbo.impl;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.lang.reflect.InvocationTargetException;

public class DubboStart {

    @Test
    public void test() throws InvocationTargetException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
        TracingBootstrap
                .main(new String[]{"com.ai.cloud.skywalking.plugin.test.dubbo.impl.DubboStart"});
    }

    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext("classpath*:provider/dubbo-provider.xml");

        classPathXmlApplicationContext.start();

        while (true) {
            Thread.sleep(100000L);
        }
    }
}
