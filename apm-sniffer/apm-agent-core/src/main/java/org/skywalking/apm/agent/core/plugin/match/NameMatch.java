package org.skywalking.apm.agent.core.plugin.match;

/**
 * @author wusheng
 */
public class NameMatch extends ClassMatch {
    private String className;

    public NameMatch(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public static NameMatch byName(String className) {
        return new NameMatch(className);
    }
}
