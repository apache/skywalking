package com.ai.cloud.skywalking.plugin.custom.localmethod;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import com.ai.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;
import test.com.ai.test.TestObject;
import test.com.ai.test.TestParam;

import java.lang.reflect.InvocationTargetException;

public class CustomLocalMethodPluginTest {

    @Test
    public void test()
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        TracingBootstrap.main(new String[]{"com.ai.cloud.skywalking.plugin.custom.localmethod.CustomLocalMethodPluginTest"});
    }

    public static void main(String[] args) throws InterruptedException {
        TestObject testObject = new TestObject();
        testObject.printlnHelloWorld();
        TestObject.staticPrintlnHelloWorld("AA", new TestParam());
        RequestSpanAssert.assertEquals(new String[][] {
                {"0", "test.com.ai.test.TestObject.printlnHelloWorld()", ""},
                {"0", "test.com.ai.test.TestObject.staticPrintlnHelloWorld()", ""}
        });
    }
}
