package com.a.eye.skywalking.registry;

import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.logging.impl.log4j2.Log4j2Resolver;
import com.a.eye.skywalking.registry.api.CenterType;
import com.a.eye.skywalking.registry.api.EventType;
import com.a.eye.skywalking.registry.api.NotifyListener;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import com.a.eye.skywalking.registry.impl.zookeeper.ZookeeperConfig;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by xin on 2016/11/12.
 */
public class RegistryCenterFactoryTest {

    private RegistryCenter registryCenter;
    private ZooKeeper zooKeeper;

    @Before
    public void setUp() throws IOException {
        LogManager.setLogResolver(new Log4j2Resolver());
        registryCenter = RegistryCenterFactory.INSTANCE.getRegistryCenter(CenterType.DEFAULT_CENTER_TYPE);
        Properties config = new Properties();
        config.setProperty(ZookeeperConfig.CONNECT_URL, "127.0.0.1:2181");
        registryCenter.start(config);
        zooKeeper = new ZooKeeper("127.0.0.1:2181", 60 * 1000, new Watcher(){
            @Override
            public void process(WatchedEvent watchedEvent) {
            }
        });
    }

    @Test
    public void testRegistry() throws KeeperException, InterruptedException {
        registryCenter.register("/a/b/c");
        assertNotNull(zooKeeper.exists("/a/b/c",false));
    }

    @After
    public void clearUp() throws KeeperException, InterruptedException {
        //zooKeeper.delete("/a", -1);
    }

    @Test
    public void testSubscribe(){
        registryCenter.subscribe("/a", new NotifyListener() {
            @Override
            public void notify(EventType type, String urls) {
                assertEquals(type, EventType.Add);
                assertEquals(urls,"b");
            }
        });

        registryCenter.register("/a/b");

        registryCenter.register("/a/d");

        registryCenter.register("/a/e");
    }
}
