package com.a.eye.skywalking.api.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/27.
 */
public class StringUtilTest {
    @Test
    public void testIsEmpty(){
        Assert.assertTrue(StringUtil.isEmpty(null));
        Assert.assertTrue(StringUtil.isEmpty(""));
        Assert.assertFalse(StringUtil.isEmpty("   "));
        Assert.assertFalse(StringUtil.isEmpty("A String"));
    }

    @Test
    public void testJoin(){
        Assert.assertNull(StringUtil.join('.'));
        Assert.assertEquals("Single part.", StringUtil.join('.', "Single part."));
        Assert.assertEquals("part1.part2.p3", StringUtil.join('.', "part1", "part2", "p3"));
    }
}
