package org.skywalking.apm.agent.core.plugin;

import org.junit.Test;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.plugin.exception.IllegalPluginDefineException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginDefineTest {

    private static final String TEST_PLUGIN = "test_plugin";
    private static final String TEST_DEFINE_CLASS = "test_define_class";

    @Test(expected = IllegalPluginDefineException.class)
    public void testIllegalPluginDefine() throws IllegalPluginDefineException {
        PluginDefine.build("illegal_plugin_define");
    }

    @Test(expected = IllegalPluginDefineException.class)
    public void testEmptyPluginDefine() throws IllegalPluginDefineException {
        PluginDefine.build("");
    }

    @Test
    public void testOffStatePlugin() throws IllegalPluginDefineException {
        PluginDefine pluginDefine = PluginDefine.build(PluginDefine.PLUGIN_OFF_PREFIX + TEST_PLUGIN + "=" + TEST_DEFINE_CLASS);
        assertFalse(pluginDefine.enable());
        assertEquals(TEST_DEFINE_CLASS, pluginDefine.getDefineClass());
    }

    @Test
    public void testDefaultStatePlugin() throws IllegalPluginDefineException {
        PluginDefine pluginDefine = PluginDefine.build(TEST_PLUGIN + "=" + TEST_DEFINE_CLASS);
        assertTrue(pluginDefine.enable());
        assertEquals(TEST_DEFINE_CLASS, pluginDefine.getDefineClass());
    }

    @Test
    public void testForceEnablePlugin() throws IllegalPluginDefineException {
        Config.Plugin.FORCE_ENABLE_PLUGINS.add(TEST_PLUGIN);
        PluginDefine pluginDefine = PluginDefine.build(PluginDefine.PLUGIN_OFF_PREFIX + TEST_PLUGIN + "=" + TEST_DEFINE_CLASS);
        assertTrue(pluginDefine.enable());
        assertEquals(TEST_DEFINE_CLASS, pluginDefine.getDefineClass());
        Config.Plugin.FORCE_ENABLE_PLUGINS.clear();
    }
}
