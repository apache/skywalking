package com.a.eye.skywalking.api.plugin;

import java.util.List;
import org.junit.Assert;
import org.junit.Test;

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
