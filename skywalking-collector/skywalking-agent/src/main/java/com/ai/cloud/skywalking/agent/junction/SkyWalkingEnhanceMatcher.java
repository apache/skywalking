package com.ai.cloud.skywalking.agent.junction;

import net.bytebuddy.description.NamedElement;

/**
 * Created by wusheng on 16/7/31.
 */
public class SkyWalkingEnhanceMatcher<T extends NamedElement> extends AbstractJunction<T> {
    @Override
    public boolean matches(T target) {
        return false;
    }
}
