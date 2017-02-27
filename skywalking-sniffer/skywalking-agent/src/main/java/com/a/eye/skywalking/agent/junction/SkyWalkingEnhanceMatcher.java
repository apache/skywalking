package com.a.eye.skywalking.agent.junction;

import com.a.eye.skywalking.api.plugin.PluginFinder;
import net.bytebuddy.description.NamedElement;

/**
 * The matcher bases on byte-buddy {@link AbstractJunction} class.
 * Judge the target class in transforming, should be enhanced or not.
 * <p>
 * Created by wusheng on 16/7/31.
 */
public class SkyWalkingEnhanceMatcher<T extends NamedElement> extends AbstractJunction<T> {

    private final PluginFinder pluginFinder;

    public SkyWalkingEnhanceMatcher(PluginFinder pluginFinder) {
        this.pluginFinder = pluginFinder;
    }

    @Override
    public boolean matches(T target) {
        return pluginFinder.exist(target.getActualName());
    }
}
