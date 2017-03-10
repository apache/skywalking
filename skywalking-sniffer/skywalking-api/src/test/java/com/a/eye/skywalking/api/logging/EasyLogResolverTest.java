package com.a.eye.skywalking.api.logging;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/28.
 */
public class EasyLogResolverTest {
    @Test
    public void testGetLogger(){
        Assert.assertTrue(new EasyLogResolver().getLogger(EasyLogResolverTest.class) instanceof EasyLogger);
    }
}
