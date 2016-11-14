package com.a.eye.skywalking.registry.impl.zookeeper;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.registry.api.*;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.ArrayList;
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

        mkdirs(createPath, true);
    }

    private void mkdirs(String path, boolean bool) {

        try {
            String[] pathArray = path.split("/");
            if (pathArray.length == 0) {
                return;
            }

            StringBuilder currentCreatePath = new StringBuilder();
            for (int i = 0; i < pathArray.length - 1; i++) {
                String pathSegment = pathArray[i];
                if (pathSegment.length() == 0) {
                    continue;
                }

                currentCreatePath.append("/").append(pathSegment);
                if (client.exists(currentCreatePath.toString(), false) == null) {
                    client.create(currentCreatePath.toString(), null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT);
                }
            }
            if (bool) {
                client.create(currentCreatePath.append("/").append(pathArray[pathArray.length - 1]).toString(), null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            } else {
                client.create(currentCreatePath.append("/").append(pathArray[pathArray.length - 1]).toString(), null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            logger.info("register path[{}] success", path);
        } catch (Exception e) {
            logger.error("Failed to create path[{}]", path, e);
        }
    }

    @Override
    public void subscribe(final String path, final NotifyListener listener) {
        try {
            if (client.exists(path, false) == null) {
                logger.warn("{} was not exists. ");
                mkdirs(path, false);
            }

            client.getChildren(path, new SubscribeWatcher(path, listener), new AsyncCallback.Children2Callback() {
                @Override
                public void processResult(int rc, String path, Object ctx, List<String> children, Stat stat) {
                    for (String child : children) {
                        listener.notify(EventType.Add, child);
                    }
                }
            }, null);
        } catch (Exception e) {
            logger.error("Failed to subscribe the path {} ", path, e);
        }
    }

    @Override
    public void start(final Properties centerConfig) {
        final ZookeeperConfig config = new ZookeeperConfig(centerConfig);
        try {
            client = new ZooKeeper(config.getConnectURL(), 60 * 1000, new ConnectWatcher(config));
            if (config.hasAuthInfo()) {
                client.addAuthInfo(config.getAutSchema(), config.getAuth());
            }
        } catch (IOException e) {
            logger.error("Failed to create zookeeper registry center [{}]", config.getConnectURL(), e);
        }
    }

    private class RetryConnected implements Runnable {

        private ZookeeperConfig config;

        public RetryConnected(ZookeeperConfig config) {
            this.config = config;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    client = new ZooKeeper(config.getConnectURL(), 60 * 1000, new ConnectWatcher(config));
                } catch (Exception e) {
                    logger.error("failed to connect zookeeper", e);
                }

                if (client.getState() == ZooKeeper.States.CONNECTED) {
                    logger.info("connected successfully!");
                    break;
                }

                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException e) {
                    logger.error("Failed to sleep.", e);
                }
            }

        }
    }


    private class ConnectWatcher implements Watcher {

        private ZookeeperConfig config;

        public ConnectWatcher(ZookeeperConfig config) {
            this.config = config;
        }

        @Override
        public void process(WatchedEvent watchedEvent) {
            if (watchedEvent.getState() == Event.KeeperState.AuthFailed) {
                logger.warn("failed to auth.auth url: {} auth schema:{} auth info:{}", config.getConnectURL(),
                        config.getAutSchema(), new String(config.getAuth()));
            }

            if (watchedEvent.getState() == Event.KeeperState.Disconnected) {
                logger.warn("Disconnected from zookeeper. retry connecting...");
                new Thread(new RetryConnected(config)).start();
            }
        }
    }


    private class SubscribeWatcher implements Watcher {
        private String path;

        private NotifyListener listener;

        private List<String> previousChildPath;

        public SubscribeWatcher(String path, NotifyListener listener) {
            this.path = path;
            this.listener = listener;
            previousChildPath = new ArrayList<String>();
        }

        @Override
        public void process(WatchedEvent event) {
            try {
                client.getChildren(path, this);

                client.getChildren(path, false, new AsyncCallback.Children2Callback() {
                    @Override
                    public void processResult(int rc, String path, Object ctx, List<String> children, Stat stat) {
                       System.out.println("aaaa");
                    }
                }, null);
            }catch (Exception e){

            }

            if (event.getType() == Event.EventType.NodeChildrenChanged) {
                notifyListener(event);
            }
        }

        private void notifyListener(WatchedEvent event) {
            try {
                List<String> tmpChildrenPath = client.getChildren(path, false);
                tmpChildrenPath.removeAll(previousChildPath);
                if (tmpChildrenPath.size() == 0) {

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
