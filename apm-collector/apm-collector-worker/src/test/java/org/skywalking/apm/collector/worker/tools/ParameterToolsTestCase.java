package org.skywalking.apm.collector.worker.tools;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class ParameterToolsTestCase {

    @Test
    public void testToString() {
        Map<String, String[]> request = new HashMap<>();
        String[] test = {"Test"};

        request.put("Key", test);

        String value = ParameterTools.INSTANCE.toString(request, "Key");
        Assert.assertEquals("Test", value);
    }
}
