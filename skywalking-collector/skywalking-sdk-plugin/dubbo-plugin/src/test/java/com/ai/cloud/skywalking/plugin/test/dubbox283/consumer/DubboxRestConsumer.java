package com.ai.cloud.skywalking.plugin.test.dubbox283.consumer;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve;
import com.ai.cloud.skywalking.plugin.test.dubbox283.interfaces.IDubboxRestInterA;
import com.ai.cloud.skywalking.plugin.test.dubbox283.interfaces.param.DubboxRestInterAParameter;
import com.ai.skywalking.testframework.api.RequestSpanAssert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

public class DubboxRestConsumer {
    private static final Log logger = LogFactory.getLog(DubboxRestConsumer.class);

    @Test
    public void test() throws InvocationTargetException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
        TracingBootstrap.main(new String[] {"com.ai.cloud.skywalking.plugin.test.dubbox283.consumer.DubboxRestConsumer"});
    }

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        new BugFixAcitve();
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:consumer/dubbox283-consumer.xml");
        IDubboxRestInterA dubboxRestInterA = context.getBean(IDubboxRestInterA.class);
        dubboxRestInterA.doBusiness(new DubboxRestInterAParameter("AAAAA"));
        RequestSpanAssert.assertEquals(new String[][] {
                {"0", "rest://127.0.0.1:20880/com.ai.cloud.skywalking.plugin.test.dubbox283.interfaces.IDubboxRestInterA.doBusiness(DubboxRestInterAParameter)", ""}});
    }
}
