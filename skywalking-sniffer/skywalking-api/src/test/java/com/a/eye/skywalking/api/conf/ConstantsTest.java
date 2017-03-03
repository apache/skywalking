package com.a.eye.skywalking.api.conf;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/28.
 */
public class ConstantsTest {
    @Test
    public void testSDKVersion(){
        Assert.assertEquals("302017", Constants.SDK_VERSION);
    }
}
