package org.skywalking.apm.agent.core.plugin.match;

/**
 * Match the class with an explicit class name.
 *
 * @author wusheng
 */
public class NameMatch implements ClassMatch {
    private String className;

    private NameMatch(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public static NameMatch byName(String className) {
        return new NameMatch(className);
    }
}
