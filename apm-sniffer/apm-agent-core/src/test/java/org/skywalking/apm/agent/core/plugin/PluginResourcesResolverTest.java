package org.skywalking.apm.agent.core.plugin;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class PluginResourcesResolverTest {
    @Test
    public void testGetResources() {
        PluginResourcesResolver resolver = new PluginResourcesResolver();

        Assert.assertTrue(resolver.getResources().size() > 0);
    }

}
