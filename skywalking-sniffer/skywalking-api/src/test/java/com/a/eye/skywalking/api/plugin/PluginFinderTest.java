package com.a.eye.skywalking.api.plugin;

import java.util.ArrayList;
import net.bytebuddy.dynamic.DynamicType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/27.
 */
public class PluginFinderTest {
    @Test
    public void testFind(){
        ArrayList<AbstractClassEnhancePluginDefine> defines = new ArrayList<AbstractClassEnhancePluginDefine>();
        defines.add(new NewTestPlugin());
        defines.add(new NewTestPlugin2());
        PluginFinder finder = new PluginFinder(defines);

        Assert.assertNotNull(finder.find("test.NewClass"));
        Assert.assertTrue(finder.exist("test.NewClass"));
    }

    @Test(expected = PluginException.class)
    public void testCanNotFind(){
        ArrayList<AbstractClassEnhancePluginDefine> defines = new ArrayList<AbstractClassEnhancePluginDefine>();
        defines.add(new NewTestPlugin());
        PluginFinder finder = new PluginFinder(defines);

        finder.find("test.NewClass2");
    }

    public class NewTestPlugin extends AbstractClassEnhancePluginDefine{
        @Override
        protected DynamicType.Builder<?> enhance(String enhanceOriginClassName,
            DynamicType.Builder<?> newClassBuilder) throws PluginException {
            return newClassBuilder;
        }

        @Override protected String enhanceClassName() {
            return "test.NewClass";
        }
    }

    public class NewTestPlugin2 extends AbstractClassEnhancePluginDefine{
        @Override
        protected DynamicType.Builder<?> enhance(String enhanceOriginClassName,
            DynamicType.Builder<?> newClassBuilder) throws PluginException {
            return newClassBuilder;
        }

        @Override protected String enhanceClassName() {
            return null;
        }
    }
}
