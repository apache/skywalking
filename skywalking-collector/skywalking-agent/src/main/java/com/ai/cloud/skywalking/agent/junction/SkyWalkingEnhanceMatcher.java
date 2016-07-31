package com.ai.cloud.skywalking.agent.junction;

import com.ai.cloud.skywalking.plugin.PluginDefineCategory;
import net.bytebuddy.description.NamedElement;

/**
 * Created by wusheng on 16/7/31.
 */
public class SkyWalkingEnhanceMatcher<T extends NamedElement> extends AbstractJunction<T> {

    private final PluginDefineCategory pluginDefineCategory;

    public SkyWalkingEnhanceMatcher(PluginDefineCategory pluginDefineCategory) {
        this.pluginDefineCategory = pluginDefineCategory;
    }

    @Override
    public boolean matches(T target) {
        return pluginDefineCategory.findPluginDefine(target.getSourceCodeName()) != null ? true : false;
    }
}
