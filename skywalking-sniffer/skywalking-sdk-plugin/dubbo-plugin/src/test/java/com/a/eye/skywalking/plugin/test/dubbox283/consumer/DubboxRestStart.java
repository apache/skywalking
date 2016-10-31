package com.a.eye.skywalking.plugin.test.dubbox283.consumer;

import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.TracingBootstrap;
import com.a.eye.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.lang.reflect.InvocationTargetException;

public class DubboxRestStart {

    @Test
    public void test() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            PluginException {
        TracingBootstrap.main(new String[] {"DubboxRestStart"});
    }

    public static void main(String[] args) throws InterruptedException {
        new BugFixAcitve();
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("classpath*:provider/dubbox283-provider.xml");

        classPathXmlApplicationContext.start();

        while (true) {
            Thread.sleep(100000L);
        }
    }
}
