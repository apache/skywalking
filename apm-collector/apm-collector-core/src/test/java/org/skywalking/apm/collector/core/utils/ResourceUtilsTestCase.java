package org.skywalking.apm.collector.core.utils;

import java.io.IOException;
import java.io.Reader;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.core.util.ResourceUtils;

public class ResourceUtilsTestCase {

    @Test
    public void testRead() throws IOException {
        Reader reader = ResourceUtils.read("application.yml");
        Assert.assertNotNull(reader);
        reader.close();
    }

}
