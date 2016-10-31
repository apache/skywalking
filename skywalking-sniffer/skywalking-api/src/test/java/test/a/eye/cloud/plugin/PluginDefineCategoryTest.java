package test.a.eye.cloud.plugin;

import com.a.eye.skywalking.plugin.AbstractClassEnhancePluginDefine;
import com.a.eye.skywalking.plugin.PluginDefineCategory;
import com.a.eye.skywalking.plugin.PluginException;
import net.bytebuddy.dynamic.DynamicType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.spy;

;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AbstractClassEnhancePluginDefine.class)
public class PluginDefineCategoryTest {

    private PluginDefineCategory pluginDefineCategory;

    @Before
    public void init() {
        PowerMockito.spy(AbstractClassEnhancePluginDefine.class);
        AbstractClassEnhancePluginDefine define1 = spy(new AbstractClassEnhancePluginDefine() {
            @Override
            protected DynamicType.Builder<?> enhance(String enhanceOriginClassName,
                    DynamicType.Builder<?> newClassBuilder) throws PluginException {
                return null;
            }

            @Override
            protected String enhanceClassName() {
                return "com.ai.test.*";
            }
        });
        List<AbstractClassEnhancePluginDefine> plugins = new ArrayList<AbstractClassEnhancePluginDefine>();
        plugins.add(define1);

        pluginDefineCategory = PluginDefineCategory.category(plugins);
    }

    @Test
    public void testCategory() throws Exception {
        assertEquals(1, pluginDefineCategory.getBlurryClassEnhancePluginDefineMapping().size());
    }

    @Test
    public void testFindPluginDef() {
        assertNotNull(pluginDefineCategory.findPluginDefine("com.ai.test.Test"));
        assertNotNull(pluginDefineCategory.findPluginDefine("com.ai.test.test.TestA"));
        assertNull(pluginDefineCategory.findPluginDefine("com.ai.test1.test"));
    }
}
