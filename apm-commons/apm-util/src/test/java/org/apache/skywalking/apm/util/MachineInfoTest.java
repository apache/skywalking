package org.apache.skywalking.apm.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author lican
 * @date 2018/4/13
 */
public class MachineInfoTest {

    @Test
    public void testMachine() {
        int processNo = MachineInfo.getProcessNo();
        Assert.assertTrue(processNo >= 0);

        String hostIp = MachineInfo.getHostIp();
        Assert.assertTrue(!StringUtil.isEmpty(hostIp));

        String hostName = MachineInfo.getHostName();
        Assert.assertTrue(!StringUtil.isEmpty(hostName));

        String hostDesc = MachineInfo.getHostDesc();
        Assert.assertTrue(!StringUtil.isEmpty(hostDesc) && hostDesc.contains("/"));
    }

}
