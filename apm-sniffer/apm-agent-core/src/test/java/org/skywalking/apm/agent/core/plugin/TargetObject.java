package org.skywalking.apm.agent.core.plugin;

public class TargetObject {

    private String value;

    public TargetObject(String value) {
        this.value = value;
    }

    public String instanceMethod() {
        return "instanceMethod";
    }

    public String instanceMethodWithException() {
        throw new RuntimeException("test exception");
    }

    public static String staticMethod() {
        return "staticMethod";
    }
}
