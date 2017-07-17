package org.skywalking.apm.plugin.jdbc.define;

import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static org.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link OracleInstrumentation} presents that skywalking intercepts the class <code>oracle.jdbc.OracleDriver
 * </code>.
 *
 * @author zhangxin
 */
public class OracleInstrumentation extends AbstractDatabaseInstrumentation {
    @Override
    protected ClassMatch enhanceClass() {
        return byName("oracle.jdbc.driver.OracleDriver");
    }
}
