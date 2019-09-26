package org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ValueAssertFailedException;

/**
 * Created by xin on 2017/7/15.
 */
public class EqualsAssertor extends ElementAssertor {

    public EqualsAssertor(String exceptedValue) {
        super(exceptedValue);
    }

    @Override
    public void assertValue(String desc, String actualValue) {
        if (!exceptedValue.equals(actualValue)) {
            throw new ValueAssertFailedException(desc, exceptedValue, actualValue);
        }
    }
}
