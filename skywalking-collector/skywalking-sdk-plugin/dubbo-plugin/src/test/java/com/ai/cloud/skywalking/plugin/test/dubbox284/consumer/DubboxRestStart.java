package com.ai.cloud.skywalking.plugin.test.dubbox284.consumer;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.lang.reflect.InvocationTargetException;

public class DubboxRestStart {

    @Test
    public void test() throws InvocationTargetException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
        TracingBootstrap
                .main(new String[]{"com.ai.cloud.skywalking.plugin.sample.dubbox284.consumer.DubboxRestStart"});
    }

    public static void main(String[] args) throws InterruptedException {
        new BugFixAcitve();
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new
                ClassPathXmlApplicationContext("classpath*:provider/dubbox284-provider.xml");

        classPathXmlApplicationContext.start();

        while (true) {
            Thread.sleep(100000L);
        }
    }
}
