package org.skywalking.apm.plugin.jdbc.define;

/**
 * {@link MysqlInstrumentation} presents that skywalking intercepts {@link com.mysql.jdbc.Driver}.
 *
 * @author zhangxin
 */
public class MysqlInstrumentation extends AbstractDatabaseInstrumentation {
    @Override
    protected String enhanceClassName() {
        return "com.mysql.jdbc.Driver";
    }
}
