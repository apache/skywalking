package com.a.eye.skywalking.registry.impl.zookeeper;

import com.a.eye.skywalking.registry.RegistryCenterFactory;
import com.a.eye.skywalking.registry.api.CenterType;
import com.a.eye.skywalking.registry.api.NotifyListener;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import com.a.eye.skywalking.registry.api.RegistryNode;
import junit.framework.TestSuite;
import org.I0Itec.zkclient.ZkClient;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.KeeperException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZookeeperRegistryCenterTest extends TestSuite {
    private TestingServer zkTestServer;
    private ZkClient zkClient;
    private RegistryCenter registryCenter;

    @Before
    public void setUp() throws Exception {
        zkTestServer = new TestingServer(42181, true);
        zkClient = new ZkClient("127.0.0.1:42181", 2000);

        registryCenter = RegistryCenterFactory.INSTANCE.getRegistryCenter(CenterType.DEFAULT_CENTER_TYPE);
        Properties config = new Properties();
        config.put(ZookeeperConfig.CONNECT_URL, "127.0.0.1:42181");
        registryCenter.start(config);
    }

    @After
    public void tearDown() throws Exception {
        zkTestServer.getTempDirectory().delete();
        zkTestServer.stop();
        registryCenter.stop();
    }

    @Test
    public void subscribeNodeTest() throws InterruptedException {
        final StringBuilder addUrl = new StringBuilder();
        registryCenter.subscribe("/skywalking/storage",  new NotifyListener() {
            @Override
            public void notify(List<RegistryNode> registryNodes) {
                for (RegistryNode url : registryNodes) {
                    addUrl.append(url.getNode() + ",");
                }
            }
        });

        registryCenter.register("/skywalking/storage/127.0.0.1:9400");
        Thread.sleep(100L);
        assertEquals(addUrl.deleteCharAt(addUrl.length() - 1).toString(), "127.0.0.1:9400");
    }


    @Test
    public void subscribeNodeAfterNodeRegistryTest() throws InterruptedException {
        registryCenter.register("/skywalking/storage/127.0.0.1:9400");
        final StringBuilder addUrl = new StringBuilder();
        registryCenter.subscribe("/skywalking/storage", new NotifyListener() {
            @Override
            public void notify(List<RegistryNode> registryNodes) {
                for (RegistryNode url : registryNodes) {
                    addUrl.append(url.getNode() + ",");
                }
            }
        });
        Thread.sleep(100L);
        assertEquals(addUrl.deleteCharAt(addUrl.length() - 1).toString(), "127.0.0.1:9400");
    }



    @Test
    public void registryNodeTest() throws IOException, InterruptedException, KeeperException {
        registryCenter.register("/skywalking/storage/test");
        assertTrue(zkClient.exists("/skywalking/storage/test"));
    }
}
