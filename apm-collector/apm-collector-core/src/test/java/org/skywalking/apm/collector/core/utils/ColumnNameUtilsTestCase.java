package org.skywalking.apm.collector.core.utils;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;

/**
 * @author pengys5
 */
public class ColumnNameUtilsTestCase {

    @Test
    public void testRename() {
        String columnName = ColumnNameUtils.INSTANCE.rename("aaa_bbb_ccc");
        Assert.assertEquals("aaaBbbCcc", columnName);
    }
}
