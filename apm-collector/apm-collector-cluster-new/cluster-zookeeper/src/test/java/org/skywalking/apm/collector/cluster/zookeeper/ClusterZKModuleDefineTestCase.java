package org.skywalking.apm.collector.cluster.zookeeper;

import java.io.FileNotFoundException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.skywalking.apm.collector.core.cluster.ClusterModuleException;
import org.yaml.snakeyaml.Yaml;

/**
 * @author pengys5
 */
public class ClusterZKModuleDefineTestCase {

    private Map config;

    @Before
    public void before() throws FileNotFoundException {
        Yaml yaml = new Yaml();
        config = (Map)yaml.load("hostPort: localhost:2181" + System.lineSeparator() + "sessionTimeout: 2000");
    }

    @Test
    public void testInitialize() throws ClusterModuleException {
        ClusterZKModuleDefine define = new ClusterZKModuleDefine();
        define.initialize(config);
    }
}
