package org.skywalking.apm.collector.client.zookeeper;

import java.io.IOException;
import java.util.List;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.skywalking.apm.collector.core.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ZookeeperClient implements Client {

    private final Logger logger = LoggerFactory.getLogger(ZookeeperClient.class);

    private ZooKeeper zk;

    private final String hostPort;
    private final int sessionTimeout;
    private final Watcher watcher;

    public ZookeeperClient(String hostPort, int sessionTimeout, Watcher watcher) {
        this.hostPort = hostPort;
        this.sessionTimeout = sessionTimeout;
        this.watcher = watcher;
    }

    @Override public void initialize() throws ZookeeperClientException {
        try {
            zk = new ZooKeeper(hostPort, sessionTimeout, watcher);
        } catch (IOException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    public void create(final String path, byte data[], List<ACL> acl,
        CreateMode createMode) throws ZookeeperClientException {
        try {
            zk.create(path, data, acl, createMode);
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    public Stat exists(final String path, boolean watch) throws ZookeeperClientException {
        try {
            return zk.exists(path, watch);
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    public byte[] getData(String path, boolean watch, Stat stat) throws ZookeeperClientException {
        try {
            return zk.getData(path, watch, stat);
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    public Stat setData(final String path, byte data[], int version) throws ZookeeperClientException {
        try {
            return zk.setData(path, data, version);
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    public List<String> getChildren(final String path, boolean watch) throws ZookeeperClientException {
        try {
            return zk.getChildren(path, watch);
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }
}
