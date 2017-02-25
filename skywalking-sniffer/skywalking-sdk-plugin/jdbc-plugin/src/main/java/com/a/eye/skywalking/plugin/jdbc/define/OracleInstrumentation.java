package com.a.eye.skywalking.plugin.jdbc.define;

/**
 * {@link OracleInstrumentation} presents that skywalking intercepts the class <code>oracle.jdbc.OracleDriver
 * </code>.
 *
 * @author zhangxin
 */
public class OracleInstrumentation extends AbstractDatabaseInstrumentation {
    @Override
    protected String enhanceClassName() {
        return "oracle.jdbc.OracleDriver";
    }
}
