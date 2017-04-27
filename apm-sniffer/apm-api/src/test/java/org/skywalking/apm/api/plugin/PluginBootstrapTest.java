package org.skywalking.apm.api.plugin;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author wusheng
 */
public class PluginBootstrapTest {
    @Test
    public void testLoadPlugins() {
        PluginBootstrap bootstrap = new PluginBootstrap();
        List<AbstractClassEnhancePluginDefine> defines = bootstrap.loadPlugins();

        Assert.assertEquals(1, defines.size());
        Assert.assertEquals(MockAbstractClassEnhancePluginDefine.class, defines.get(0).getClass());
    }
}
