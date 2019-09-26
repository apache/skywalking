package org.apache.skywalking.plugin.test.agent.tool.validator.assertor.element;

public abstract class ElementAssertor {

    public ElementAssertor(String exceptedValue) {
        if (exceptedValue != null) {
            this.exceptedValue = exceptedValue.trim();
        }
    }

    protected String exceptedValue;

    public abstract void assertValue(String desc, String actualValue);
}
