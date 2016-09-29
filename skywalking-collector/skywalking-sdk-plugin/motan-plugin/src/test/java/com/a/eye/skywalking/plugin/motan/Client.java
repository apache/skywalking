package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.TracingBootstrap;
import com.a.eye.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by xin on 16/9/27.
 */
public class Client {
    @Test
    public void test()
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            PluginException {
        TracingBootstrap.main(new String[] {Client.class.getName()});
    }

    public static void main(String[] args) throws InterruptedException {
        ApplicationContext ctx = new ClassPathXmlApplicationContext("classpath:motan_client.xml");
        FooService service = (FooService) ctx.getBean("remoteService");
        System.out.println(service.hello("motan", "1"));
        RequestSpanAssert
                .assertEquals(new String[][] {{"0", "motan://localhost:8002/com.a.eye.skywalking.plugin.motan.FooService.hello(java.lang.String,java.lang.String)?group=default_rpc", ""}});
    }
}
