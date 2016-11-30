package com.a.eye.skywalking.routing.storage.listener;

import com.a.eye.skywalking.registry.RegistryCenterFactory;
import com.a.eye.skywalking.registry.api.NotifyListener;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import com.a.eye.skywalking.registry.impl.zookeeper.ZookeeperConfig;
import com.a.eye.skywalking.routing.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

import static com.a.eye.skywalking.routing.storage.listener.NotifyListenerImpl.ChangeType.Add;
import static com.a.eye.skywalking.routing.storage.listener.NotifyListenerImpl.ChangeType.Removed;

public class NotifyListenerImpl implements NotifyListener {

    private NodeChangesListener listener;
    private List<String> childrenConnectionURLOfPreviousChanged = new ArrayList<>();
    private ReentrantLock lock = new ReentrantLock();

    public NotifyListenerImpl(String subscribePath, NodeChangesListener listener) {
        this.listener = listener;
        RegistryCenter center = RegistryCenterFactory.INSTANCE.getRegistryCenter(Config.RegistryCenter.TYPE);
        center.start(fetchRegistryCenterConfig());
        center.subscribe(subscribePath, this::notify);
    }

    private Properties fetchRegistryCenterConfig() {
        Properties centerConfig = new Properties();
        centerConfig.setProperty(ZookeeperConfig.CONNECT_URL, Config.RegistryCenter.CONNECT_URL);
        centerConfig.setProperty(ZookeeperConfig.AUTH_SCHEMA, Config.RegistryCenter.AUTH_SCHEMA);
        centerConfig.setProperty(ZookeeperConfig.AUTH_INFO, Config.RegistryCenter.AUTH_INFO);
        return centerConfig;
    }

    @Override
    public void notify(List<String> currentUrls) {
        lock.lock();
        try {
            //TODO: bug, logic error.
            List<String> URL = new ArrayList<>(currentUrls);
            if (childrenConnectionURLOfPreviousChanged.size() > URL.size()) {
                childrenConnectionURLOfPreviousChanged.removeAll(URL);
                listener.notify(childrenConnectionURLOfPreviousChanged, Removed);
            } else {
                URL.removeAll(childrenConnectionURLOfPreviousChanged);
                listener.notify(URL, Add);
            }

            childrenConnectionURLOfPreviousChanged = new ArrayList<>(URL);
        } finally {
            lock.unlock();
        }

    }

    public enum ChangeType {
        Removed, Add;
    }

}
