package com.a.eye.skywalking.routing.router;

import com.a.eye.skywalking.registry.RegistryCenterFactory;
import com.a.eye.skywalking.registry.api.CenterType;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import com.a.eye.skywalking.registry.impl.zookeeper.ZookeeperConfig;
import com.a.eye.skywalking.routing.config.Config;
import com.a.eye.skywalking.routing.storage.listener.NotifyListenerImpl;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class NodeChangesListenerTest {

    @Spy
    private Router router = new Router();

    private TestingServer zkTestServer;
    private RegistryCenter registryCenter;
    private NotifyListenerImpl notifyListenerImpl;

    @Before
    public void setUp() throws Exception {
        zkTestServer = new TestingServer(2181, true);
        registryCenter = RegistryCenterFactory.INSTANCE.getRegistryCenter(CenterType.DEFAULT_CENTER_TYPE);
        Properties config = new Properties();
        config.put(ZookeeperConfig.CONNECT_URL, "127.0.0.1:2181");
        registryCenter.start(config);

    }

    @Test
    public void testRoutingStartBeforeStorageNode() throws InterruptedException {
        notifyListenerImpl = new NotifyListenerImpl(Config.StorageNode.SUBSCRIBE_PATH, router);
        registryCenter.register(Config.StorageNode.SUBSCRIBE_PATH + "/127.0.0.1:34000");
        Thread.sleep(10);
        List<String> nodeURL = new ArrayList<>();
        nodeURL.add("127.0.0.1:34000");
        //verify(router, times(1)).notify(eq(nodeURL), eq(NotifyListenerImpl.ChangeType.Add));
    }


    @Test
    public void testStorageNodeStartBeforeRoutingStart() throws InterruptedException {
        registryCenter.register(Config.StorageNode.SUBSCRIBE_PATH + "/127.0.0.1:34000");
        notifyListenerImpl = new NotifyListenerImpl(Config.StorageNode.SUBSCRIBE_PATH, router);
        Thread.sleep(10);
        List<String> nodeURL = new ArrayList<>();
        nodeURL.add("127.0.0.1:34000");
        //verify(router, times(1)).notify(eq(nodeURL), eq(NotifyListenerImpl.ChangeType.Add));
    }

    @After
    public void tearUp() throws IOException {
        zkTestServer.stop();
    }

}