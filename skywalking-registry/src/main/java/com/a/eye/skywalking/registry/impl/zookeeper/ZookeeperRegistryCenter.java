package com.a.eye.skywalking.registry.impl.zookeeper;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.registry.api.*;
import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Center(type = CenterType.DEFAULT_CENTER_TYPE)
public class ZookeeperRegistryCenter implements RegistryCenter {

    private ILog logger = LogManager.getLogger(ZookeeperRegistryCenter.class);

    public ZooKeeper client;

    @Override
    public void register(String path) {
        String createPath = path;
        if (path.charAt(0) != '/') {
            createPath = "/" + createPath;
        }

        mkdirs(createPath, 0);
    }

    /**
     * @param path
     * @param index
     */
    private void mkdirs(String path, int index) {
        //TODO: 修改成循环创建
        try {
            int next = path.indexOf("/", index + 1);
            CreateMode createMode = CreateMode.EPHEMERAL;

            if (next != -1) {
                createMode = CreateMode.PERSISTENT;
                path = path.substring(0, next);
            }

            if (client.exists(path, false) == null)
                client.create(path, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);

            if (next != -1) {
                mkdirs(path, next);
            }
        } catch (Exception e) {
            logger.error("Failed to create path[{}]", path, e);
        }
    }


    @Override
    public void subscribe(final String path, final NotifyListener listener) {
        try {
            List<String> childrenPath = client.getChildren(path, new SubscribeWatcher(path, listener));
            for (String child : childrenPath) {
                listener.notify(EventType.Add, child);
            }
        } catch (Exception e) {
            logger.error("Failed to subscribe the path {} ", path, e);
        }
    }

    @Override
    public void start(Properties centerConfig) {
        ZookeeperConfig config = new ZookeeperConfig(centerConfig);
        try {
            client = new ZooKeeper(config.getConnectURL(), 60 * 1000, null);
            if (config.hasAuthInfo()) {
                client.addAuthInfo(config.getAutSchema(), config.getAuth());
            }
        } catch (IOException e) {
            logger.error("Failed to create zookeeper registry center [{}]", config.getConnectURL(), e);
        }
    }


    private class SubscribeWatcher implements Watcher {
        private String path;

        private NotifyListener listener;

        public SubscribeWatcher(String path, NotifyListener listener) {
            this.path = path;
            this.listener = listener;
        }

        @Override
        public void process(WatchedEvent event) {
            retryWatch();

            if (event.getType() == Event.EventType.NodeChildrenChanged) {
                notifyListener(event);
            }
        }

        private void notifyListener(WatchedEvent event) {
            try {
                List<String> tmpChildrenPath = client.getChildren(path, null);
                if (tmpChildrenPath.contains(event.getPath())) {
                    listener.notify(EventType.Add, event.getPath());
                } else {
                    listener.notify(EventType.Remove, event.getPath());
                }
            } catch (Exception e) {
                logger.error("Failed to fetch path[{}] children.", path, e);
            }
        }

        private void retryWatch() {
            try {
                client.getChildren(path, this);
            } catch (Exception e) {
                logger.error("Failed to rewatch path[{}]", path, e);
            }
        }
    }
}
