package org.apache.skywalking.oap.server.core;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

public class CoreModuleConfigTest {
    @Test
    public void testRoleFromNameNormalSituation() {
        Assert.assertEquals(CoreModuleConfig.Role.Mixed, CoreModuleConfig.Role.fromName("Mixed"));
        Assert.assertEquals(CoreModuleConfig.Role.Receiver, CoreModuleConfig.Role.fromName("Receiver"));
        Assert.assertEquals(CoreModuleConfig.Role.Aggregator, CoreModuleConfig.Role.fromName("Aggregator"));
    }
    @Test
    public void testRoleFromNameBlockParameter() {
        Assert.assertEquals(CoreModuleConfig.Role.Mixed, CoreModuleConfig.Role.fromName(StringUtils.EMPTY));
        Assert.assertEquals(CoreModuleConfig.Role.Mixed, CoreModuleConfig.Role.fromName(null));
    }
    @Test
    public void testRoleFromNameNotIncludeRole() {
        Assert.assertEquals(CoreModuleConfig.Role.Mixed, CoreModuleConfig.Role.fromName("a"));
    }
}
