package org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ValueAssertFailedException;

/**
 * Created by xin on 2017/7/18.
 */
public class NullAssertor extends ElementAssertor {
    public NullAssertor() {
        super(null);
    }

    @Override
    public void assertValue(String desc, String actualValue) {
        if (actualValue != null && actualValue.length() > 0) {
            throw new ValueAssertFailedException(desc, "null", actualValue);
        }
    }
}
