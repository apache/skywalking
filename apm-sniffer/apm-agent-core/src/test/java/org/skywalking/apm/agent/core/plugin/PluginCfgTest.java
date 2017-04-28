package org.skywalking.apm.agent.core.plugin;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by wusheng on 2017/2/27.
 */
public class PluginCfgTest {
    @Test
    public void testLoad() throws IOException {
        String data = "com.test.classA\r\ncom.test.ClassB";
        final byte[] dataBytes = data.getBytes();
        PluginCfg.INSTANCE.load(new InputStream() {
            int index = 0;

            @Override
            public int read() throws IOException {
                if (index == dataBytes.length) {
                    return -1;
                }
                return dataBytes[index++];
            }
        });

        List<String> list = PluginCfg.INSTANCE.getPluginClassList();
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("com.test.classA", list.get(0));
        Assert.assertEquals("com.test.ClassB", list.get(1));
    }

    @Before
    @After
    public void clear() throws IllegalAccessException {
        MemberModifier.field(PluginCfg.class, "pluginClassList").set(PluginCfg.INSTANCE, new ArrayList<String>());
    }
}
