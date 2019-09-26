package org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ValueAssertFailedException;

/**
 * Created by xin on 2017/7/15.
 */
public class NotNullAssertor extends ElementAssertor {
    public NotNullAssertor() {
        super(null);
    }

    @Override
    public void assertValue(String desc, String actualValue) {
        if (actualValue == null) {
            throw new ValueAssertFailedException(desc, "not null", actualValue);
        }
    }
}
