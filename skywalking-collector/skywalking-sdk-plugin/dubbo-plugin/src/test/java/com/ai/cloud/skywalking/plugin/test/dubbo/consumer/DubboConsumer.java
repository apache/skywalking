package com.ai.cloud.skywalking.plugin.test.dubbo.consumer;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import com.ai.cloud.skywalking.plugin.test.dubbo.interfaces.IDubboInterA;
import com.ai.skywalking.testframework.api.TraceTreeAssert;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.lang.reflect.InvocationTargetException;

public class DubboConsumer {


    @Test
    public void test() throws InvocationTargetException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
        TracingBootstrap
                .main(new String[]{"com.ai.cloud.skywalking.plugin.test.dubbo.consumer.DubboConsumer"});
    }

    public static void main(String[] args) throws InterruptedException {
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:consumer/dubbo-consumer.xml");
        IDubboInterA dubboInterA = context.getBean(IDubboInterA.class);
        dubboInterA.doBusiness("AAAAA");
        TraceTreeAssert.assertEquals(new String[][]{
                {"0", "dubbo://127.0.0.1:20880/com.ai.cloud.skywalking.plugin.test.dubbo.interfaces.IDubboInterA.doBusiness(String)", ""}
        });
    }
}
