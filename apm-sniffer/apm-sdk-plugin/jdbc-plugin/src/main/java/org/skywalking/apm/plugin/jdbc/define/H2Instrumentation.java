package org.skywalking.apm.plugin.jdbc.define;

import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static org.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link H2Instrumentation} presents that skywalking intercepts {@link org.h2.Driver}.
 *
 * @author zhangxin
 */
public class H2Instrumentation extends AbstractDatabaseInstrumentation {

    private static final String CLASS_OF_INTERCEPT_H2_DRIVER = "org.h2.Driver";

    @Override
    protected ClassMatch enhanceClass() {
        return byName(CLASS_OF_INTERCEPT_H2_DRIVER);
    }
}
