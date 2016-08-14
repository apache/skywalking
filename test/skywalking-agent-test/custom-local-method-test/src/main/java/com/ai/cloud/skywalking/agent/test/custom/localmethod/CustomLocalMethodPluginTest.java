package com.a.eye.skywalking.agent.test.custom.localmethod;

import com.ai.skywalking.testframework.api.RequestSpanAssert;
import test.com.a.eye.skywalking.agent.test.custom.localmethod.TestObject;
import test.com.a.eye.skywalking.agent.test.custom.localmethod.TestParam;

public class CustomLocalMethodPluginTest {

    public static void main(String[] args) throws InterruptedException {
        TestObject testObject = new TestObject();
        TestObject.staticPrintlnHelloWorld("AAAA", new TestParam("A", "B"));
        testObject.printlnHelloWorld(new TestParam("B", "C"));

        RequestSpanAssert.assertEquals(new String[][] {
                {"0", "test.com.a.eye.skywalking.agent.test.custom.localmethod.TestObject.printlnHelloWorld()", ""},
                {"0", "test.com.a.eye.skywalking.agent.test.custom.localmethod.TestObject.staticPrintlnHelloWorld()", ""}
        },true);
    }
}
