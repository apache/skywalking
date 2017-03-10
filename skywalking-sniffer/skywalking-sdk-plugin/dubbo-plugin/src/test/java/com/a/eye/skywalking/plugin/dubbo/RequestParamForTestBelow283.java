package com.a.eye.skywalking.plugin.dubbo;

import com.a.eye.skywalking.plugin.dubbox.SWBaseBean;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * {@link RequestParamForTestBelow283} store context data for test.
 */
public class RequestParamForTestBelow283 extends SWBaseBean {

    /**
     * This method assert that {@link SWBaseBean#getTraceContext()} if it's not null and context data
     * will end with the expect span id.
     */
    public void assertSelf(String expectHost) {
        assertNotNull(getTraceContext());
        assertThat(getTraceContext(), endsWith(expectHost));
    }
}
