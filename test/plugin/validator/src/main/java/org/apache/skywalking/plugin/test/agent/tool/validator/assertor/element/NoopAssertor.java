package org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element;

/**
 * Created by xin on 2017/7/15.
 */
public class NoopAssertor extends ElementAssertor {
    public NoopAssertor() {
        super(null);
    }

    @Override
    public void assertValue(String desc, String actualValue) {

    }
}
