package com.a.eye.skywalking.plugin.jdbc.define;

/**
 * {@link MysqlInstrumentation} presents that skywalking will intercept {@link com.mysql.jdbc.Driver}.
 *
 * @author zhangxin
 */
public class MysqlInstrumentation extends AbstractDatabaseInstrumentation {
    @Override
    protected String enhanceClassName() {
        return "com.mysql.jdbc.Driver";
    }
}
