package org.skywalking.apm.plugin.jdbc.define;

import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static org.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link MysqlInstrumentation} presents that skywalking intercepts {@link com.mysql.jdbc.Driver}.
 *
 * @author zhangxin
 */
public class MysqlInstrumentation extends AbstractDatabaseInstrumentation {
    @Override
    protected ClassMatch enhanceClass() {
        return byName("com.mysql.jdbc.Driver");
    }
}
