package com.a.eye.skywalking.sniffer.mock.plugin;

import org.junit.Test;

import static net.bytebuddy.matcher.ElementMatchers.named;


public class InstrumentationAssertTest {

    @Test
    public void testAssert() {

        new InstrumentationAssert(Object.class).enhanceClass("")
                .constructor().withParamType(Class.class, Object.class);
                /*.and(assertMethod("").withParamType())*/

//        new InstrumentationAssert(Object.class).enhanceClass("").allMethod()
//                .and(assertConstructor().withParamType(Class.class, Object.class));
    }


}