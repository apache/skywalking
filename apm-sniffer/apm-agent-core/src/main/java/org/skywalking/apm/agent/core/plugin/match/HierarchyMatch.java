package org.skywalking.apm.agent.core.plugin.match;

import net.bytebuddy.matcher.ElementMatcher;

/**
 * @author wusheng
 */
public class HierarchyMatch extends ClassMatch {
    private String[] parentTypes;

    public HierarchyMatch(String[] parentTypes) {
        this.parentTypes = parentTypes;
    }

    public ElementMatcher.Junction buildJunction() {
        return null;
    }

    private ElementMatcher.Junction buildEachParent(String parentType) {
        return null;
    }
}
