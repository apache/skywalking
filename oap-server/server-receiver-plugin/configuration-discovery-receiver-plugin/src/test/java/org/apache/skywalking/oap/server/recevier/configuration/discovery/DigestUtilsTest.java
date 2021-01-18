package org.apache.skywalking.oap.server.recevier.configuration.discovery;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Assert;
import org.junit.Test;

public class DigestUtilsTest {
    @Test
    public void testMd5Hex() {
        String text =
            "configurations:\n" +
                "  serviceA:\n" +
                "    trace.sample_rate: 1000\n" +
                "    trace.ignore_path: /api/seller/seller/*\n" +
                "  serviceB:\n" +
                "    trace.sample_rate: 1000\n" +
                "    trace.ignore_path: /api/seller/seller/*\n";
        String md5Hex = DigestUtils.md5Hex(text);
        Assert.assertEquals("d52342af66661d0e72e5f5caf6457f35", md5Hex);

        String text1 = text +
            "  serviceA:\n" +
            "    trace.sample_rate: 1000\n" +
            "    trace.ignore_path: /api/seller/seller/*\n";
        Assert.assertNotEquals("d52342af66661d0e72e5f5caf6457f35", DigestUtils.md5Hex(text1));
    }
}
