package com.a.eye.skywalking.plugin.jdbc.define;

/**
 * {@link H2Instrumentation} presents that skywalking will intercept {@link org.h2.Driver}.
 *
 * @author zhangxin
 */
public class H2Instrumentation extends AbstractDatabaseInstrumentation {

    /**
     * Class of intercept H2 driver
     */
    private static final String CLASS_OF_INTERCEPT_H2_DRIVER = "org.h2.Driver";

    @Override
    protected String enhanceClassName() {
        return CLASS_OF_INTERCEPT_H2_DRIVER;
    }
}
