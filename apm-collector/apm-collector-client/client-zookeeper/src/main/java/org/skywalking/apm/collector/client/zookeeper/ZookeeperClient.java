package org.skywalking.apm.collector.client.zookeeper;

import java.io.IOException;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ZookeeperClient implements Client {

    private final Logger logger = LoggerFactory.getLogger(ZookeeperClient.class);

    private ZooKeeper zk;

    @Override public void initialize() throws ZookeeperClientException {
        try {
            zk = new ZooKeeper(ZookeeperConfig.hostPort, ZookeeperConfig.sessionTimeout, new ZookeeperDataListener(this));
        } catch (IOException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    @Override public void insert(String path) throws ZookeeperClientException {
        logger.info("add the zookeeper path \"{}\"", path);
        try {
            zk.create(path, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    @Override public void update() {

    }

    @Override public String select(String path) throws ZookeeperClientException {
        logger.info("get the zookeeper data from path \"{}\"", path);
        try {
            return zk.getData(path, false, null).toString();
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    @Override public void delete() {

    }

    @Override public boolean exist(String path) throws ZookeeperClientException {
        logger.info("assess the zookeeper path \"{}\" exist", path);
        try {
            Stat stat = zk.exists(path, false);
            if (ObjectUtils.isEmpty(stat)) {
                return false;
            } else {
                return true;
            }
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }

    @Override public void listen(String path) throws ZookeeperClientException {
        try {
            zk.exists(path, true);
        } catch (KeeperException | InterruptedException e) {
            throw new ZookeeperClientException(e.getMessage(), e);
        }
    }
}
