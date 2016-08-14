package com.a.eye.skywalking.plugin.test.dubbo.impl;

import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.TracingBootstrap;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.lang.reflect.InvocationTargetException;

public class DubboStart {

    @Test
    public void test() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            PluginException {
        TracingBootstrap.main(new String[] {"DubboStart"});
    }

    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:provider/dubbo-provider.xml");

        classPathXmlApplicationContext.start();

        while (true) {
            Thread.sleep(100000L);
        }
    }
}
