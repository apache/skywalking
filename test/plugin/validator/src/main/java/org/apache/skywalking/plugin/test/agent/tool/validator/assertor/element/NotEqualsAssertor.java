package org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ValueAssertFailedException;

/**
 * Created by xin on 2017/7/15.
 */
public class NotEqualsAssertor extends ElementAssertor {

    public NotEqualsAssertor(String exceptedValue) {
        super(exceptedValue);
    }

    @Override
    public void assertValue(String desc, String actualValue) {
        if (exceptedValue.equals(actualValue.trim())) {
            throw new ValueAssertFailedException(desc, " not eq " + exceptedValue, actualValue);
        }
    }
}
