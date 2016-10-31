package com.a.eye.skywalking.plugin.test.dubbo.consumer;

import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.TracingBootstrap;
import com.a.eye.skywalking.plugin.test.dubbo.interfaces.IDubboInterA;
import com.a.eye.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.lang.reflect.InvocationTargetException;

public class DubboConsumer {


    @Test
    public void test() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            PluginException {
        TracingBootstrap.main(new String[] {"DubboConsumer"});
    }

    public static void main(String[] args) throws InterruptedException {
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:consumer/dubbo-consumer.xml");
        IDubboInterA dubboInterA = context.getBean(IDubboInterA.class);
        dubboInterA.doBusiness("AAAAA");
        RequestSpanAssert.assertEquals(new String[][] {{"0", "dubbo://127.0.0.1:20880/IDubboInterA.doBusiness(String)", ""}});
    }
}
